package kr.ject.naspi.websocket.application.topic.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.ject.naspi.market.application.LeagueStockLookupService;
import kr.ject.naspi.market.application.LeagueStockView;
import kr.ject.naspi.market.application.StockQuote;
import kr.ject.naspi.websocket.api.response.stock.StockResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockMessageQueryTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 21, 30, 1);

    private final LeagueStockLookupService lookupService = mock(LeagueStockLookupService.class);
    private final Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
    private final StockMessageQuery query = new StockMessageQuery(lookupService, clock);

    @Test
    @DisplayName("종목 정보 메시지의 asOf는 조회 직전 시각(KST)이고 시세가 없는 종목은 시세 필드가 null이다")
    void asOfIsCalculationTime() {
        given(lookupService.findAllInLeague(1L)).willReturn(List.of(
                new LeagueStockView(1L, "수빈전자", "소개", "수빈",
                        new StockQuote(12_500, 10_000, 13_000, 7_000, 2_500,
                                new BigDecimal("25.0"), 5)),
                new LeagueStockView(2L, "민준건설", "소개2", "민준", null)));

        StockResponse response = query.getStocks(1L);

        assertThat(response.asOf()).isEqualTo(NOW);
        assertThat(response.stocks()).containsExactly(
                new StockResponse.Stock(1L, "수빈전자", "소개", "수빈", 12_500L, 10_000L, 13_000L,
                        7_000L, 2_500L, new BigDecimal("25.0"), 5L),
                new StockResponse.Stock(2L, "민준건설", "소개2", "민준", null, null, null, null,
                        null, null, null));
    }
}
