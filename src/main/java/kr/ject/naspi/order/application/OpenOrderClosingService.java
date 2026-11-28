package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueTradingService;
import kr.ject.naspi.league.application.MemberPlayer;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderRepository;
import kr.ject.naspi.order.domain.OrderStatus;
import kr.ject.naspi.order.domain.OrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미체결 주문의 일괄 종료 (거래일 만료 ORD-09, 회원 탈퇴 취소 ORD-11, 리그 종료 취소 ORD-10).
 *
 * <p>리그 락 책임은 메서드마다 다르다. {@link #expireTradingDay}는 호출자가 리그 락을 잡은 트랜잭션에서
 * 호출해야 한다 ({@code MANDATORY}는 트랜잭션 존재만 확인하고 리그 락 보유는 확인하지 못한다. 리그 락 없이
 * 호출하면 접수·취소·체결과 직렬화되지 않아 예약이 두 번 해제될 수 있다). {@link #cancelAllOfMember}·
 * {@link #cancelAllOfLeague}는 메서드가 직접 {@code LeagueTradingService#lockForClosing}으로 락을 잡는다.
 * 락 순서는 리그 락 → asset 락이다. 주문 행 락은 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OpenOrderClosingService {

    private final OrderRepository orderRepository;
    private final OrderExecutionSupport support;
    private final LeagueQueryService leagueQueryService;
    private final LeagueTradingService leagueTradingService;

    /**
     * 마감하는 거래일 이하에 접수한 지정가 미체결 주문(대기·부분 체결)을 만료시키고 미체결 잔여 수량의 예약
     * 자산을 해제한다. 이미 체결된 부분은 유지된다.
     *
     * <p>호출자는 같은 트랜잭션에서 리그 행 락을 잡고 있어야 한다 (리그 락 → asset 락). 리그 락을 직접 잡지
     * 않는 이유는 거래 가능 판정이 종료 시각 이후를 거절하므로, 종료 후 기동 시 밀린 전환 보정에서 쓸 수 없기
     * 때문이다. 시장가 주문은 접수 트랜잭션에서 잔량이 바로 취소되어 열린 상태로 남지 않으므로 대상이 아니다.
     *
     * @param leagueId 리그 ID
     * @param dayNumber 마감하는 거래일 일차 (이 일차 이하의 주문이 대상)
     * @param expiredAt 만료 시각 (새 거래일 시작 시각, 09:00:00)
     * @return 만료 처리한 주문 수와 주문이 만료된 플레이어
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ExpiredOrders expireTradingDay(long leagueId, int dayNumber, LocalDateTime expiredAt) {
        List<Order> orders = orderRepository
                .findAllByLeagueIdAndTypeAndStatusInAndTradingDayLessThanEqualOrderByIdAsc(
                        leagueId, OrderType.LIMIT, OrderStatus.openStatuses(), dayNumber);
        int count = closeAll(orders, CancelReason.TRADING_DAY_EXPIRED, expiredAt);
        return new ExpiredOrders(count, orders.stream()
                .map(Order::getPlayerId)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * 회원이 참여 중인(진행 중인) 모든 리그의 미체결 주문(대기·부분 체결)을 회원 탈퇴 사유로 취소하고 미체결 잔여
     * 수량의 예약 자산을 해제한다. 이미 체결된 부분은 유지된다. 회원·플레이어 매핑이 남아 있는 탈퇴 트랜잭션에서
     * 현금 폐기 전에 호출한다 (예약이 남아 있으면 폐기가 거절된다).
     *
     * <p>리그마다 leagueId 오름차순으로 리그 락을 잡고(서로 다른 회원의 탈퇴가 같은 순서로 잠가 교착을 막는다),
     * 락을 잡은 뒤 주문을 읽는다. 종료 시각이 지났지만 아직 시작 상태인 리그도 거래 가능 판정 없이 처리한다.
     * {@code MANDATORY}인 이유는 리그 락이 탈퇴 트랜잭션 커밋까지 유지되어야 하기 때문이다. 그래야 취소 뒤 현금
     * 폐기 전에 같은 플레이어의 새 주문이 끼어들지 못한다. 별도 트랜잭션으로 먼저 커밋하면 그 사이 새 주문이
     * 예약을 만들어 폐기가 거절될 수 있다.
     *
     * @param memberId 탈퇴하는 회원 ID
     * @return 취소한 주문 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelAllOfMember(long memberId) {
        List<MemberPlayer> players = leagueQueryService.findStartedPlayersOfMember(memberId)
                .stream()
                .sorted(Comparator.comparingLong(MemberPlayer::leagueId))
                .toList();
        int canceled = 0;
        for (MemberPlayer player : players) {
            LocalDateTime canceledAt = leagueTradingService.lockForClosing(player.leagueId());
            List<Order> orders = orderRepository.findAllByPlayerIdAndStatusInOrderByIdDesc(
                    player.playerId(), OrderStatus.openStatuses());
            canceled += closeAll(orders, CancelReason.MEMBER_WITHDRAWN, canceledAt);
        }
        return canceled;
    }

    /**
     * 리그의 지정가 미체결 주문(대기·부분 체결)을 거래일 구분 없이 모두 리그 종료 사유로 취소하고 미체결 잔여
     * 수량의 예약 자산을 해제한다. 이미 체결된 부분은 유지된다.
     *
     * <p>{@code lockForClosing}으로 리그 락을 잡은 뒤 주문을 읽는다. 종료 시각 도달 여부는 확인하지 않는다
     * (호출 시점은 리그 종료 UseCase의 책임이다). 호출자는 밀린 거래일 전환을 먼저 끝낸 뒤 호출해야 한다.
     * {@code MANDATORY}인 이유는 리그 락이 리그 종료 트랜잭션 커밋까지 유지되어야 이후의 결과 산정이 예약 0
     * 상태를 보기 때문이다. 별도 트랜잭션으로 먼저 커밋하면 락이 풀려 종료 처리와 직렬화되지 않는다.
     *
     * <p>시장가 주문은 접수 트랜잭션에서 잔량이 바로 취소되어 열린 상태로 남지 않으므로 대상이 아니다
     * ({@link #expireTradingDay}와 같다). 모든 유형을 대상으로 하는 {@link #cancelAllOfMember}와 유형 기준이
     * 다르다.
     *
     * @param leagueId 리그 ID
     * @param endedAt 취소 시각 (호출자가 넘기는 리그 종료 시각)
     * @return 취소한 주문 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelAllOfLeague(long leagueId, LocalDateTime endedAt) {
        leagueTradingService.lockForClosing(leagueId);
        List<Order> orders = orderRepository.findAllByLeagueIdAndTypeAndStatusInOrderByIdAsc(
                leagueId, OrderType.LIMIT, OrderStatus.openStatuses());
        return closeAll(orders, CancelReason.LEAGUE_ENDED, endedAt);
    }

    private int closeAll(List<Order> orders, CancelReason reason, LocalDateTime closedAt) {
        for (Order order : orders) {
            support.closeRemaining(order, reason, closedAt);
        }
        return orders.size();
    }
}
