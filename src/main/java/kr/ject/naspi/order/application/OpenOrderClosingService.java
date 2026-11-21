package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import java.util.List;
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
 * 미체결 주문의 일괄 종료 (거래일 만료 ORD-09). 리그 종료(ORD-10)·회원 탈퇴(ORD-11)의 일괄 취소도 같은
 * 클래스에 공개 메서드를 더해 {@link #closeAll}을 재사용한다.
 *
 * <p>호출자는 리그 락을 잡은 트랜잭션에서 호출해야 한다. {@code MANDATORY}는 트랜잭션 존재만 확인하고
 * 리그 락 보유는 확인하지 못한다. 리그 락 없이 호출하면 접수·취소·체결과 직렬화되지 않아 예약이 두 번
 * 해제될 수 있다. 락 순서는 리그 락 → asset 락이다. 리그 락 없이 부르는 경로(예: 회원 탈퇴)의 락 정책은
 * ND-43·ND-44가 정해야 한다.
 */
@Service
@RequiredArgsConstructor
public class OpenOrderClosingService {

    private final OrderRepository orderRepository;
    private final OrderExecutionSupport support;

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
     * @return 만료 처리한 주문 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int expireTradingDay(long leagueId, int dayNumber, LocalDateTime expiredAt) {
        List<Order> orders = orderRepository
                .findAllByLeagueIdAndTypeAndStatusInAndTradingDayLessThanEqualOrderByIdAsc(
                        leagueId, OrderType.LIMIT, OrderStatus.openStatuses(), dayNumber);
        return closeAll(orders, CancelReason.TRADING_DAY_EXPIRED, expiredAt);
    }

    private int closeAll(List<Order> orders, CancelReason reason, LocalDateTime closedAt) {
        for (Order order : orders) {
            support.closeRemaining(order, reason, closedAt);
        }
        return orders.size();
    }
}
