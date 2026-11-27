package kr.ject.naspi.order.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.order.domain.Execution;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderExecutionTotal;
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
    @DisplayName("종목의 체결을 체결 일시가 늦은 순서로 조회한다")
    void findsRecentTradesFirst() {
        Trade oldest = save(1L, 1, 10_000, 1, NOW);
        Trade newest = save(1L, 1, 10_000, 1, NOW.plusMinutes(2));
        Trade middle = save(1L, 1, 10_000, 1, NOW.plusMinutes(1));
        save(2L, 1, 10_000, 1, NOW.plusMinutes(3));
        entityManager.flush();

        List<Trade> trades = tradeRepository.findRecentByStockId(1L, 500);

        assertThat(trades).containsExactly(newest, middle, oldest);
    }

    @Test
    @DisplayName("최근 체결은 최대 limit건만 조회한다")
    void findRecentLimitsCount() {
        save(1L, 1, 10_000, 1, NOW);
        Trade middle = save(1L, 1, 10_000, 1, NOW.plusMinutes(1));
        Trade newest = save(1L, 1, 10_000, 1, NOW.plusMinutes(2));
        entityManager.flush();

        assertThat(tradeRepository.findRecentByStockId(1L, 2)).containsExactly(newest, middle);
    }

    private Trade saveWithOrders(long sellOrderId, long buyOrderId, LocalDateTime executedAt) {
        Order sell = order(10, 1L, OrderSide.SELL, 10_000, 5, 1);
        ReflectionTestUtils.setField(sell, "id", sellOrderId);
        Order buy = order(20, 1L, OrderSide.BUY, 10_000, 5, 1);
        ReflectionTestUtils.setField(buy, "id", buyOrderId);
        return tradeRepository.save(
                Trade.record(new Execution(sell, buy, 10_000, 1), 1, executedAt));
    }

    @Test
    @DisplayName("매수 주문의 체결만 체결 일시·ID 오름차순으로 조회한다")
    void findAllByBuyOrderId_ordered() {
        Trade late = saveWithOrders(100L, 7L, NOW.plusMinutes(1));
        Trade early = saveWithOrders(101L, 7L, NOW);
        Trade sameTimeAfter = saveWithOrders(102L, 7L, NOW);
        saveWithOrders(103L, 8L, NOW);
        entityManager.flush();

        assertThat(tradeRepository.findAllByBuyOrderIdOrderByExecutedAtAscIdAsc(7L))
                .containsExactly(early, sameTimeAfter, late);
    }

    @Test
    @DisplayName("매도 주문의 체결만 체결 일시·ID 오름차순으로 조회한다")
    void findAllBySellOrderId_ordered() {
        Trade late = saveWithOrders(7L, 100L, NOW.plusMinutes(1));
        Trade early = saveWithOrders(7L, 101L, NOW);
        saveWithOrders(8L, 102L, NOW);
        entityManager.flush();

        assertThat(tradeRepository.findAllBySellOrderIdOrderByExecutedAtAscIdAsc(7L))
                .containsExactly(early, late);
    }

    private void saveTrade(long sellOrderId, long buyOrderId, long price, long quantity) {
        Order sell = order(10, 1L, OrderSide.SELL, price, quantity, 1);
        ReflectionTestUtils.setField(sell, "id", sellOrderId);
        Order buy = order(20, 1L, OrderSide.BUY, price, quantity, 1);
        ReflectionTestUtils.setField(buy, "id", buyOrderId);
        tradeRepository.save(Trade.record(new Execution(sell, buy, price, quantity), 1, NOW));
    }

    @Test
    @DisplayName("매수 주문 ID 목록별로 체결 수량·금액 합계와 최저·최고 체결가를 한 번에 구한다")
    void sumByBuyOrderIds() {
        saveTrade(100L, 7L, 10_000, 1);
        saveTrade(101L, 7L, 10_100, 2);
        saveTrade(102L, 8L, 11_000, 5);
        saveTrade(103L, 9L, 12_000, 4);
        entityManager.flush();

        assertThat(tradeRepository.sumByBuyOrderIds(List.of(7L, 8L)))
                .containsExactlyInAnyOrder(
                        new OrderExecutionTotal(7L, 3, 30_200, 10_000, 10_100),
                        new OrderExecutionTotal(8L, 5, 55_000, 11_000, 11_000));
    }

    @Test
    @DisplayName("매도 주문 ID 목록별 체결 합계를 구하고 체결 없는 주문은 결과에 없다")
    void sumBySellOrderIds() {
        saveTrade(7L, 100L, 10_000, 1);
        saveTrade(7L, 101L, 10_100, 2);
        saveTrade(9L, 102L, 12_000, 4);
        entityManager.flush();

        assertThat(tradeRepository.sumBySellOrderIds(List.of(7L, 8L)))
                .containsExactly(new OrderExecutionTotal(7L, 3, 30_200, 10_000, 10_100));
    }
}
