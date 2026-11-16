package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderBookTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);
    private static final long STOCK_ID = 100L;

    private static Order limit(long id, OrderSide side, long price, long quantity) {
        Order order = Order.limit(1L, 10L + id, STOCK_ID, side, price, quantity, 1, NOW);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @Test
    @DisplayName("매수는 높은 가격 우선, 같은 가격에서는 먼저 접수된 주문 우선이다")
    void bidPriority() {
        OrderBook book = OrderBook.of(STOCK_ID, List.of(
                limit(3, OrderSide.BUY, 10_000, 1),
                limit(1, OrderSide.BUY, 9_000, 1),
                limit(2, OrderSide.BUY, 10_000, 1),
                limit(4, OrderSide.BUY, 11_000, 1)));

        assertThat(book.orders(OrderSide.BUY)).extracting(Order::getId)
                .containsExactly(4L, 2L, 3L, 1L);
        assertThat(book.bestBid()).contains(11_000L);
    }

    @Test
    @DisplayName("매도는 낮은 가격 우선, 같은 가격에서는 먼저 접수된 주문 우선이다")
    void askPriority() {
        OrderBook book = OrderBook.of(STOCK_ID, List.of(
                limit(3, OrderSide.SELL, 10_000, 1),
                limit(1, OrderSide.SELL, 11_000, 1),
                limit(2, OrderSide.SELL, 10_000, 1),
                limit(4, OrderSide.SELL, 9_000, 1)));

        assertThat(book.orders(OrderSide.SELL)).extracting(Order::getId)
                .containsExactly(4L, 2L, 3L, 1L);
        assertThat(book.bestAsk()).contains(9_000L);
    }

    @Test
    @DisplayName("가격 단계별로 잔여 수량과 주문 수를 합산한다")
    void levels() {
        Order partial = limit(1, OrderSide.BUY, 10_000, 30);
        partial.fill(10);
        OrderBook book = OrderBook.of(STOCK_ID, List.of(
                partial,
                limit(2, OrderSide.BUY, 10_000, 5),
                limit(3, OrderSide.BUY, 9_000, 7)));

        assertThat(book.levels(OrderSide.BUY)).containsExactly(
                new OrderBookLevel(10_000, 25, 2),
                new OrderBookLevel(9_000, 7, 1));
    }

    @Test
    @DisplayName("체결 완료·취소·만료된 주문과 시장가 주문은 호가창에 담기지 않는다")
    void excludesNonResting() {
        Order filled = limit(1, OrderSide.BUY, 10_000, 1);
        filled.fill(1);
        Order canceled = limit(2, OrderSide.BUY, 10_000, 1);
        canceled.cancel();
        Order expired = limit(3, OrderSide.SELL, 10_000, 1);
        expired.expire();
        Order market = Order.market(1L, 10L, STOCK_ID, OrderSide.BUY, 1, 1, NOW);
        ReflectionTestUtils.setField(market, "id", 4L);

        OrderBook book = OrderBook.of(STOCK_ID, List.of(filled, canceled, expired, market));

        assertThat(book.orders(OrderSide.BUY)).isEmpty();
        assertThat(book.orders(OrderSide.SELL)).isEmpty();
        assertThat(book.bestBid()).isEmpty();
        assertThat(book.bestAsk()).isEmpty();
    }
}
