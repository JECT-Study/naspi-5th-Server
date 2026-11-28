package kr.ject.naspi.league.application;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.league.domain.TradingDayRepository;
import kr.ject.naspi.market.application.StockTradingDayService;
import kr.ject.naspi.order.application.ExpiredOrders;
import kr.ject.naspi.order.application.OpenOrderClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래일 전환 (매일 09:00 KST).
 *
 * <p>정책 (docs/policies/v1/domain/market.md "시장"): 매일 오전 9시 KST에 거래일이 1일씩 증가하고, 직전 거래일의
 * 종가가 확정되며 새 거래일의 기준가가 적용된다. 미체결 주문은 거래일 끝에 만료된다.
 *
 * <p>거래일 N+1로 전환할 때 거래일 N을 마감한다. 리그 행을 잠근 채 한 리그의 전환을 한 트랜잭션으로 처리한다.
 * 주문 접수·취소·체결도 같은 락을 쓰므로 전환 중에는 그 리그의 주문이 대기한다. 마감한 거래일은
 * {@code trading_day.closed_at}에 기록하므로 서버 재시작·중복 실행·여러 서버의 동시 실행에도 같은 전환을 한 번만
 * 처리하고, 놓친 전환은 다음 실행에서 일차 순서대로 보정한다. 마지막 거래일의 마감은 리그 종료 처리(LEA-12)가 맡는다.
 *
 * <p>후속 처리는 하위 모듈 application을 {@link #changeTo}에서 직접 호출한다 (README "거래일 전환"):
 * market 종가 확정·기준가 적용(MKT-06)을 호출하고, 이어서 order 미체결 지정가 주문 만료·예약
 * 해제(ORD-09)를 호출한다. 해제 중 오류가 나면 그 리그의 전환 전체가 롤백되어 다음 실행에서 보정된다.
 *
 * <p>전환을 처리하면 리그당 한 번 {@link TradingDayChanged}를 발행한다. 종료 시각이 지난 리그(리그 종료 처리 중
 * 보정, 종료 후 기동 시 보정)는 발행하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TradingDayChangeUseCase {

    private final LeagueRepository leagueRepository;
    private final TradingDayRepository tradingDayRepository;
    private final StockTradingDayService stockTradingDayService;
    private final OpenOrderClosingService openOrderClosingService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 진행 중인 리그의 밀린 거래일 전환을 처리한다.
     *
     * @param leagueId 리그 ID
     * @param changedAt 실행 시각
     * @return 처리한 전환 수 (진행 중이 아니거나 처리할 전환이 없으면 0)
     */
    @Transactional
    public int change(long leagueId, LocalDateTime changedAt) {
        League league = leagueRepository.findWithLockById(leagueId).orElse(null);
        if (league == null || league.getStatus() != LeagueStatus.STARTED) {
            return 0;
        }
        List<TradingDay> closures = TradingDay.pendingClosures(
                tradingDayRepository.findAllByLeagueIdOrderByDayNumber(leagueId), changedAt);
        Set<Long> expiredPlayerIds = new HashSet<>();
        for (TradingDay closingDay : closures) {
            expiredPlayerIds.addAll(changeTo(closingDay).playerIds());
            closingDay.close(changedAt);
        }
        // 리그 종료 처리 중 밀린 전환 보정에서는 거래일 갱신 알림을 보내지 않는다 (정책 "주문 알림")
        if (!closures.isEmpty() && !league.isEndDue(changedAt)) {
            int newDayNumber = closures.get(closures.size() - 1).getDayNumber() + 1;
            eventPublisher.publishEvent(new TradingDayChanged(leagueId, newDayNumber,
                    Set.copyOf(expiredPlayerIds)));
        }
        return closures.size();
    }

    /**
     * 마감하는 거래일의 다음 거래일로 전환한다. 하위 모듈 호출 시각은 실행 시각이 아니라 새 거래일의 시작 시각
     * (마감하는 거래일의 종료 시각, 09:00:00)이다.
     */
    private ExpiredOrders changeTo(TradingDay closingDay) {
        stockTradingDayService.changeTradingDay(
                closingDay.getLeagueId(), closingDay.getDayNumber());
        return openOrderClosingService.expireTradingDay(
                closingDay.getLeagueId(), closingDay.getDayNumber(), closingDay.getEndsAt());
    }
}
