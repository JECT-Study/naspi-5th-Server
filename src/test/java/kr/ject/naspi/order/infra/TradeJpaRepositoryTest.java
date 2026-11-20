package kr.ject.naspi.order.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.order.domain.Execution;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.Trade;
import kr.ject.naspi.order.domain.TradeRepository;
import kr.ject.naspi.order.domain.TradeTotal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
class TradeJpaRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Trade save(long stockId, int tradingDay, long price, long quantity,
            LocalDateTime executedAt) {
        Order sell = order(10, stockId, OrderSide.SELL, price, quantity, tradingDay);
        Order buy = order(20, stockId, OrderSide.BUY, price, quantity, tradingDay);
        return tradeRepository.save(
                Trade.record(new Execution(sell, buy, price, quantity), tradingDay, executedAt));
    }

    private Order order(long playerId, long stockId, OrderSide side, long price, long quantity,
            int tradingDay) {
        Order order = Order.limit(1L, playerId, stockId, side, price, quantity, tradingDay, NOW);
        ReflectionTestUtils.setField(order, "id", 1L);
        return order;
    }

    @Test
    @DisplayName("종목별·거래일별로 체결 수량 합계와 가격 × 수량 합계를 구한다")
    void sumsByStockAndTradingDay() {
        save(1L, 1, 10_000, 10, NOW);
        save(1L, 1, 11_000, 5, NOW.plusMinutes(1));
        save(1L, 2, 20_000, 7, NOW.plusDays(1));
        save(2L, 1, 30_000, 9, NOW);
        entityManager.flush();

        assertThat(tradeRepository.sumByStockIdAndTradingDay(1L, 1))
                .isEqualTo(new TradeTotal(15, 155_000));
        assertThat(tradeRepository.sumByStockIdAndTradingDay(1L, 2))
                .isEqualTo(new TradeTotal(7, 140_000));
    }

    @Test
    @DisplayName("체결이 없는 종목·거래일의 합계는 0이다")
    void sumIsZeroWithoutTrades() {
        assertThat(tradeRepository.sumByStockIdAndTradingDay(1L, 3))
                .isEqualTo(new TradeTotal(0, 0));
    }

    @Test
    @DisplayName("종목의 체결을 종료 시각에 가까운 순서로 최대 500건 조회한다")
    void findsRecentTradesFirst() {
        Trade oldest = save(1L, 1, 10_000, 1, NOW);
        Trade newest = save(1L, 1, 10_000, 1, NOW.plusMinutes(2));
        Trade middle = save(1L, 1, 10_000, 1, NOW.plusMinutes(1));
        save(2L, 1, 10_000, 1, NOW.plusMinutes(3));
        entityManager.flush();

        List<Trade> trades = tradeRepository.findTop500ByStockIdOrderByExecutedAtDescIdDesc(1L);

        assertThat(trades).containsExactly(newest, middle, oldest);
    }
}
