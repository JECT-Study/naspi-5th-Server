package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
@Import({StockTradingDayService.class, StockExecutionService.class})
class StockTradingDayServiceTest {

    private static final long LEAGUE_ID = 1L;

    @Autowired
    private StockTradingDayService service;

    @Autowired
    private StockExecutionService executionService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("직전 거래일 종가가 새 거래일 기준가가 되고 새 상·하한가가 적용된다")
    void closeAndApplyNewBase() {
        long stockId = openedStock();
        executionService.applyExecution(LEAGUE_ID, stockId, 1, 11_000, 3);

        service.changeTradingDay(LEAGUE_ID, 1);

        assertThat(day(stockId, 1).getClosePrice()).isEqualTo(11_000);
        StockDailyPrice next = day(stockId, 2);
        assertThat(next.getBasePrice()).isEqualTo(11_000);
        assertThat(next.getLowerLimitPrice()).isEqualTo(7_700);
        assertThat(next.getUpperLimitPrice()).isEqualTo(14_300);
        assertThat(next.getClosePrice()).isNull();
        assertThat(entityManager.find(Stock.class, stockId).getCurrentPrice())
                .isEqualTo(11_000);
    }

    @Test
    @DisplayName("거래량 0이면 종가는 기준가이고 다음 기준가도 같다")
    void noVolumeKeepsBase() {
        long stockId = openedStock();

        service.changeTradingDay(LEAGUE_ID, 1);

        assertThat(day(stockId, 1).getClosePrice()).isEqualTo(10_000);
        assertThat(day(stockId, 2).getBasePrice()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("새 거래일 거래량은 0부터 시작한다")
    void resetsVolume() {
        long stockId = openedStock();
        executionService.applyExecution(LEAGUE_ID, stockId, 1, 10_500, 7);

        service.changeTradingDay(LEAGUE_ID, 1);

        assertThat(day(stockId, 1).getVolume()).isEqualTo(7);
        assertThat(day(stockId, 2).getVolume()).isZero();
    }

    @Test
    @DisplayName("종가가 100원 단위로 절사되는 기준가에서 상·하한가를 계산한다")
    void limitBoundaryRounding() {
        long stockId = openedStock();
        executionService.applyExecution(LEAGUE_ID, stockId, 1, 12_300, 1);

        service.changeTradingDay(LEAGUE_ID, 1);

        assertThat(day(stockId, 2).getLowerLimitPrice()).isEqualTo(8_700);
        assertThat(day(stockId, 2).getUpperLimitPrice()).isEqualTo(15_900);
    }

    @Test
    @DisplayName("마감하는 거래일의 시세 행이 없으면 전환할 수 없다")
    void rejectsMissingRow() {
        Stock stock = Stock.list(LEAGUE_ID, 10L, StockName.of("가나다"), StockDescription.of("소개"));
        stock.open();
        entityManager.persistAndFlush(stock);

        assertThatThrownBy(() -> service.changeTradingDay(LEAGUE_ID, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("전환 트랜잭션 밖에서는 호출할 수 없다")
    void requiresTransaction() {
        assertThatThrownBy(() -> service.changeTradingDay(LEAGUE_ID, 1))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private long openedStock() {
        Stock stock = Stock.list(LEAGUE_ID, 10L, StockName.of("가나다"), StockDescription.of("소개"));
        stock.open();
        long stockId = entityManager.persistAndGetId(stock, Long.class);
        entityManager.persist(StockDailyPrice.open(LEAGUE_ID, stockId, 1, 10_000));
        entityManager.flush();
        return stockId;
    }

    private StockDailyPrice day(long stockId, int tradingDay) {
        entityManager.flush();
        return entityManager.getEntityManager().createQuery(
                        "select d from StockDailyPrice d where d.stockId = :s "
                                + "and d.tradingDay = :t", StockDailyPrice.class)
                .setParameter("s", stockId).setParameter("t", tradingDay).getSingleResult();
    }
}
