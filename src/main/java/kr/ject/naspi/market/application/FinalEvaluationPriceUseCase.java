package kr.ject.naspi.market.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.market.domain.EvaluatedTrade;
import kr.ject.naspi.market.domain.FinalEvaluationPrice;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockRepository;
import kr.ject.naspi.market.domain.TradeVolume;
import kr.ject.naspi.order.application.TradeDailyTotal;
import kr.ject.naspi.order.application.TradeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 종료 평가 가격 (MKT-07). 리그 모든 종목의 종료 평가 가격(VWAP)을 구한다.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 결과"): 마지막 거래일 VWAP, 500주 미만이면 종료에 가까운 과거
 * 체결로 500주까지 확대, 리그 전체 500주 미만이면 전체 체결, 체결이 없으면 시초가. 규칙은
 * {@link FinalEvaluationPrice}에 있다.
 *
 * <p>하위 모듈 order의 체결 기록 조회({@link TradeQueryService})를 쓰므로 UseCase다. 거래일 종가 기준 평가와는
 * 다르다.
 */
@Service
@RequiredArgsConstructor
public class FinalEvaluationPriceUseCase {

    private final StockRepository stockRepository;
    private final TradeQueryService tradeQueryService;

    /**
     * 리그 모든 종목의 종료 평가 가격을 구한다. 리그 락을 잡은 리그 종료 트랜잭션 안에서 호출한다 (락 밖에서
     * 호출하면 계산 중 체결이 생길 수 있다).
     *
     * <p>종료 시각 이후에는 주문 접수·체결이 거절되어 체결 기록이 생기지 않으므로, 저장된 체결은 모두 종료 시각
     * 직전까지의 체결이다. 따라서 체결 일시로 종료 시각을 따로 거르지 않는다. 마지막 거래일 체결은 거래일 번호가
     * {@code lastTradingDay}인 체결이다.
     *
     * @param leagueId 리그 ID
     * @param lastTradingDay 마지막 거래일 번호 (호출자가 {@code TradingSchedule.TRADING_DAYS}를 넘긴다)
     * @return 종목 ID → 1주당 원. 리그의 모든 종목이며 종목 ID 순이다
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> getFinalEvaluationPrices(long leagueId, int lastTradingDay) {
        List<Stock> stocks = stockRepository.findAllByLeagueId(leagueId).stream()
                .sorted(Comparator.comparing(Stock::getId))
                .toList();
        Map<Long, Long> prices = new LinkedHashMap<>();
        for (Stock stock : stocks) {
            prices.put(stock.getId(), evaluate(stock.getId(), lastTradingDay));
        }
        return prices;
    }

    private long evaluate(long stockId, int lastTradingDay) {
        TradeDailyTotal lastDay = tradeQueryService.getDailyTotal(stockId, lastTradingDay);
        return FinalEvaluationPrice.evaluate(
                new TradeVolume(lastDay.quantity(), lastDay.amount()),
                () -> tradeQueryService
                        .findRecent(stockId, FinalEvaluationPrice.MAX_RECENT_TRADES).stream()
                        .map(trade -> new EvaluatedTrade(trade.price(), trade.quantity()))
                        .toList());
    }
}
