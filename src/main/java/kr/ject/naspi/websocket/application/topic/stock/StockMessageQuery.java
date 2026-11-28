package kr.ject.naspi.websocket.application.topic.stock;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.market.application.LeagueStockLookupService;
import kr.ject.naspi.market.application.LeagueStockView;
import kr.ject.naspi.market.application.StockQuote;
import kr.ject.naspi.websocket.api.response.stock.StockResponse;
import kr.ject.naspi.websocket.api.response.stock.StockResponse.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 종목 정보 메시지 조회. REST와 같은 종목 조회({@link LeagueStockLookupService})에 계산 시각({@code asOf})을
 * 붙인다.
 */
@Component
@RequiredArgsConstructor
class StockMessageQuery {

    private final LeagueStockLookupService lookupService;
    private final Clock clock;

    /**
     * 리그 전체 종목의 메시지를 만든다. 계산 시각은 조회 직전 시각이다.
     *
     * @param leagueId 리그 ID
     * @return 종목 ID 순 종목 목록과 계산 시각
     */
    public StockResponse getStocks(long leagueId) {
        LocalDateTime asOf = LocalDateTime.now(clock);
        return new StockResponse(lookupService.findAllInLeague(leagueId).stream()
                .map(StockMessageQuery::toStock)
                .toList(), asOf);
    }

    private static Stock toStock(LeagueStockView view) {
        StockQuote quote = view.quote();
        if (quote == null) {
            return new Stock(view.stockId(), view.name(), view.description(), view.listerName(),
                    null, null, null, null, null, null, null);
        }
        return new Stock(view.stockId(), view.name(), view.description(), view.listerName(),
                quote.currentPrice(), quote.basePrice(), quote.upperLimitPrice(),
                quote.lowerLimitPrice(), quote.changeAmount(), quote.changeRate(),
                quote.volume());
    }
}
