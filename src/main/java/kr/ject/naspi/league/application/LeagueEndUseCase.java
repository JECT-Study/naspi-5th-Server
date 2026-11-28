package kr.ject.naspi.league.application;

import java.time.LocalDateTime;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.TradingDayRepository;
import kr.ject.naspi.league.domain.TradingSchedule;
import kr.ject.naspi.market.application.StockTradingDayService;
import kr.ject.naspi.order.application.OpenOrderClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 종료 (LEA-12). 종료 시각(시작의 KST 날짜 + 7일, 09:00 KST)이 지난 진행 중인 리그를 종료한다.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 시작 및 운영", "리그 결과"): 리그는 종료 시각에 종료되며 종료일
 * 변경과 조기 종료는 할 수 없다. 종료 시각 직전까지 체결된 거래를 결과에 포함하고, 미체결 주문은 모두 취소한다.
 * 종료 시각부터는 리그 결과 페이지만 접근할 수 있다.
 *
 * <p>리그 행을 잠근 채 한 리그의 종료를 한 트랜잭션으로 처리한다. 주문 접수·취소·체결과 거래일 전환도 같은 락을
 * 쓰므로 직렬화되고, 종료 상태({@code ENDED})로 바뀐 리그는 다시 처리하지 않으므로 서버 재시작·중복 실행·여러
 * 서버의 동시 실행에도 한 번만 처리된다. 처리 순서는 다음과 같다 (README "리그 종료").
 * <ol>
 *   <li>밀린 거래일 전환 보정: 1~6일차 중 마감하지 않은 거래일을 일차 순으로 마감한다
 *       ({@link TradingDayChangeUseCase}).</li>
 *   <li>7일차 마감: market 7일차 종가 확정(MKT-06) 후 거래일 마감을 기록한다.</li>
 *   <li>order 미체결 지정가 주문 전체 취소·예약 해제(ORD-10). 취소 시각은 리그 종료 시각이다.</li>
 *   <li>market 종료 평가 가격(MKT-07)으로 asset 총자산 집계(AST-08) 후 순위·결과 스냅샷 저장(LEA-13,
 *       {@link LeagueResultUseCase}). 취소가 끝나 예약 자산이 0인 상태에서 산정해야 하므로 미체결 취소 뒤,
 *       종료 상태 전환 전에 같은 트랜잭션에서 호출한다. 결과 산정이 같은 집계로 7일차 거래일별 자산 집계(LEA-18)도
 *       저장한다.</li>
 *   <li>리그 종료 상태 전환과 초대 코드 폐기, 종료 이벤트 발행.</li>
 * </ol>
 *
 * <p>처리 중 오류가 나면 그 리그의 종료 전체가 롤백되어 진행 중 상태로 남고 다음 실행에서 다시 처리된다. 그 사이에도
 * 거래·리그 화면 접근은 종료 시각 기준으로 차단된다.
 */
@Service
@RequiredArgsConstructor
public class LeagueEndUseCase {

    private final LeagueRepository leagueRepository;
    private final TradingDayRepository tradingDayRepository;
    private final TradingDayChangeUseCase tradingDayChangeUseCase;
    private final StockTradingDayService stockTradingDayService;
    private final OpenOrderClosingService openOrderClosingService;
    private final LeagueResultUseCase leagueResultUseCase;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 종료 시각이 지난 진행 중인 리그를 종료한다.
     *
     * @param leagueId 리그 ID
     * @param now 실행 시각
     * @return 종료 처리했으면 true. 리그가 없거나, 진행 중이 아니거나, 종료 시각 전이면 false
     */
    @Transactional
    public boolean end(long leagueId, LocalDateTime now) {
        // 주문 접수·취소·체결·거래일 전환과 같은 리그 락. lockForTrading은 종료 시각 이후를 거절하므로 쓰지 않는다.
        League league = leagueRepository.findWithLockById(leagueId).orElse(null);
        if (league == null || !league.isEndDue(now)) {
            return false;
        }
        // 1~6일차 중 놓친 거래일 전환을 먼저 끝내 7일차 시세 행까지 만든다
        tradingDayChangeUseCase.change(leagueId, now);
        // 7일차 종가 확정과 마감 기록
        stockTradingDayService.closeLastTradingDay(leagueId, TradingSchedule.TRADING_DAYS);
        tradingDayRepository.findByLeagueIdAndDayNumber(leagueId, TradingSchedule.TRADING_DAYS)
                .orElseThrow(() -> new IllegalStateException(
                        "마지막 거래일이 없습니다. leagueId=" + leagueId))
                .closeLast(now);
        // 남은 지정가 미체결 주문은 거래일 구분 없이 리그 종료 사유로 취소한다
        openOrderClosingService.cancelAllOfLeague(leagueId, league.getEndsAt());
        // 종료 평가 가격으로 최종 총자산·순위를 산정해 결과 스냅샷을 저장한다
        leagueResultUseCase.calculate(leagueId, league.getEndsAt(), now);
        league.end(now);
        eventPublisher.publishEvent(new LeagueEnded(leagueId, league.getEndsAt()));
        return true;
    }
}
