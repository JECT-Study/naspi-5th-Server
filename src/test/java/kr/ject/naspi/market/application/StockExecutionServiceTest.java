package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@DataJpaTest
@Import(StockExecutionService.class)
class StockExecutionServiceTest {

    private static final long LEAGUE_ID = 1L;

    @Autowired
    private StockExecutionService service;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("체결이 발생하면 종목의 현재가와 그 거래일 거래량이 갱신된다")
    void updatesPriceAndDailyVolume() {
        long stockId = openedStock();

        service.applyExecution(LEAGUE_ID, stockId, 1, 10_500, 3);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Stock.class, stockId).getCurrentPrice()).isEqualTo(10_500);
        assertThat(dailyPrices(stockId)).singleElement().satisfies(daily -> {
            assertThat(daily.getLeagueId()).isEqualTo(LEAGUE_ID);
            assertThat(daily.getTradingDay()).isEqualTo(1);
            assertThat(daily.getVolume()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("거래일 거래량은 체결 1건당 1회 합산되고 현재가는 마지막 체결가가 된다")
    void accumulatesPerExecution() {
        long stockId = openedStock();

        service.applyExecution(LEAGUE_ID, stockId, 1, 10_500, 3);
        service.applyExecution(LEAGUE_ID, stockId, 1, 10_300, 1);
        service.applyExecution(LEAGUE_ID, stockId, 1, 10_400, 5);
        entityManager.flush();
        entityManager.clear();

        assertThat(dailyPrices(stockId)).singleElement()
                .extracting(StockDailyPrice::getVolume).isEqualTo(9L);
        assertThat(entityManager.find(Stock.class, stockId).getCurrentPrice()).isEqualTo(10_400);
    }

    @Test
    @DisplayName("거래량은 거래일별로 따로 집계된다")
    void separatesTradingDays() {
        long stockId = openedStock();

        entityManager.persist(StockDailyPrice.open(LEAGUE_ID, stockId, 2, 10_500));
        service.applyExecution(LEAGUE_ID, stockId, 1, 10_500, 5);
        service.applyExecution(LEAGUE_ID, stockId, 2, 10_600, 2);
        entityManager.flush();
        entityManager.clear();

        assertThat(dailyPrices(stockId))
                .extracting(StockDailyPrice::getTradingDay, StockDailyPrice::getVolume)
                .containsExactly(tuple(1, 5L),
                        tuple(2, 2L));
    }

    @Test
    @DisplayName("다른 리그의 종목이나 없는 종목에는 체결을 반영할 수 없다")
    void rejectsStockOfOtherLeague() {
        long stockId = openedStock();

        assertThatThrownBy(() -> service.applyExecution(LEAGUE_ID + 1, stockId, 1, 10_500, 3))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.applyExecution(LEAGUE_ID, 9_999L, 1, 10_500, 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("거래가 열리지 않은 종목에는 체결을 반영할 수 없다")
    void rejectsUnopenedStock() {
        long stockId = entityManager.persistAndGetId(newStock(), Long.class);

        assertThatThrownBy(() -> service.applyExecution(LEAGUE_ID, stockId, 1, 10_500, 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("시세 행이 없는 거래일에는 체결을 반영할 수 없다")
    void rejectsMissingRow() {
        long stockId = openedStock();

        assertThatThrownBy(() -> service.applyExecution(LEAGUE_ID, stockId, 2, 10_500, 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("체결 트랜잭션 밖에서는 체결을 반영할 수 없다")
    void requiresTransaction() {
        assertThatThrownBy(() -> service.applyExecution(LEAGUE_ID, 1L, 1, 10_500, 3))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private long openedStock() {
        Stock stock = newStock();
        stock.open();
        long stockId = entityManager.persistAndGetId(stock, Long.class);
        entityManager.persist(StockDailyPrice.open(LEAGUE_ID, stockId, 1, 10_000));
        entityManager.flush();
        return stockId;
    }

    private Stock newStock() {
        return Stock.list(LEAGUE_ID, 10L, StockName.of("가나다"), StockDescription.of("소개"));
    }

    private List<StockDailyPrice> dailyPrices(long stockId) {
        return entityManager.getEntityManager().createQuery(
                        "select d from StockDailyPrice d where d.stockId = :s "
                                + "order by d.tradingDay",
                        StockDailyPrice.class)
                .setParameter("s", stockId).getResultList();
    }
}
