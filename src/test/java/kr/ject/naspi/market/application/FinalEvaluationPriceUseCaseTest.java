package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.order.application.TradeQueryService;
import kr.ject.naspi.order.domain.Execution;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/** 리그 종료 평가 가격(MKT-07)을 실제 체결 기록 조회(order)와 함께 계산하는지 확인한다. */
@ActiveProfiles("test")
@DataJpaTest
@Import({FinalEvaluationPriceUseCase.class, TradeQueryService.class})
class FinalEvaluationPriceUseCaseTest {

    private static final long LEAGUE_ID = 1L;
    private static final int LAST_DAY = 7;
    private static final LocalDateTime DAY6 = LocalDateTime.of(2026, 11, 20, 10, 0);
    private static final LocalDateTime DAY7 = LocalDateTime.of(2026, 11, 21, 10, 0);

    @Autowired
    private FinalEvaluationPriceUseCase useCase;

    @Autowired
    private TestEntityManager entityManager;

    private long listerSeq = 9_000L;

    private long stock(long leagueId) {
        listerSeq++;
        Stock stock = Stock.list(leagueId, listerSeq, StockName.of("종목" + listerSeq),
                StockDescription.of("소개"));
        return entityManager.persistAndFlush(stock).getId();
    }

    private void trade(long leagueId, long stockId, int tradingDay, long price, long quantity,
            LocalDateTime executedAt) {
        Order sell = order(leagueId, 10L, stockId, OrderSide.SELL, price, quantity, tradingDay,
                executedAt);
        Order buy = order(leagueId, 20L, stockId, OrderSide.BUY, price, quantity, tradingDay,
                executedAt);
        entityManager.persist(Trade.record(new Execution(sell, buy, price, quantity), tradingDay,
                executedAt));
    }

    private Order order(long leagueId, long playerId, long stockId, OrderSide side, long price,
            long quantity, int tradingDay, LocalDateTime orderedAt) {
        Order order = Order.limit(leagueId, playerId, stockId, side, price, quantity, tradingDay,
                orderedAt);
        ReflectionTestUtils.setField(order, "id", playerId);
        return order;
    }

    @Test
    @DisplayName("리그의 모든 종목에 종료 평가 가격을 돌려주고 체결 없는 종목은 시초가다")
    void evaluatesAllStocksOfLeague() {
        long enough = stock(LEAGUE_ID);
        long expanded = stock(LEAGUE_ID);
        final long noTrades = stock(LEAGUE_ID);
        // 7일차 500주: (300 × 10,000 + 200 × 11,000) / 500
        trade(LEAGUE_ID, enough, LAST_DAY, 10_000, 300, DAY7);
        trade(LEAGUE_ID, enough, LAST_DAY, 11_000, 200, DAY7.plusMinutes(1));
        // 7일차 100주 + 6일차 400주: (100 × 10,000 + 400 × 12,000) / 500
        trade(LEAGUE_ID, expanded, LAST_DAY - 1, 12_000, 400, DAY6);
        trade(LEAGUE_ID, expanded, LAST_DAY, 10_000, 100, DAY7);
        entityManager.flush();

        Map<Long, Long> prices = useCase.getFinalEvaluationPrices(LEAGUE_ID, LAST_DAY);

        assertThat(prices).containsExactly(Map.entry(enough, 10_400L),
                Map.entry(expanded, 11_600L), Map.entry(noTrades, Stock.OPENING_PRICE));
    }

    @Test
    @DisplayName("7거래일 체결만 마지막 거래일로 본다")
    void lastTradingDayIsGivenDay() {
        long stockId = stock(LEAGUE_ID);
        // 6일차 600주는 마지막 거래일이 아니다
        trade(LEAGUE_ID, stockId, LAST_DAY - 1, 9_000, 300, DAY6);
        trade(LEAGUE_ID, stockId, LAST_DAY - 1, 12_000, 300, DAY6.plusHours(1));
        trade(LEAGUE_ID, stockId, LAST_DAY, 10_000, 100, DAY7);
        entityManager.flush();

        // 7일차 100 × 10,000 + 6일차 최근 300 × 12,000 + 100 × 9,000 = 5,500,000 / 500
        assertThat(useCase.getFinalEvaluationPrices(LEAGUE_ID, LAST_DAY))
                .containsExactly(Map.entry(stockId, 11_000L));
    }

    @Test
    @DisplayName("다른 리그의 종목과 체결은 포함하지 않는다")
    void excludesOtherLeagueStocks() {
        long stockId = stock(LEAGUE_ID);
        long otherStockId = stock(2L);
        trade(LEAGUE_ID, stockId, LAST_DAY, 10_500, 10, DAY7);
        trade(2L, otherStockId, LAST_DAY, 20_000, 600, DAY7.plusMinutes(1));
        entityManager.flush();

        assertThat(useCase.getFinalEvaluationPrices(LEAGUE_ID, LAST_DAY))
                .containsExactly(Map.entry(stockId, 10_500L));
    }
}
