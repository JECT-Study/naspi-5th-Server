package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderMatcherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);
    private static final long STOCK_ID = 100L;

    private long nextId = 1;

    private Order limit(long playerId, OrderSide side, long price, long quantity) {
        Order order = Order.limit(1L, playerId, STOCK_ID, side, price, quantity, 1, NOW);
        ReflectionTestUtils.setField(order, "id", nextId++);
        return order;
    }

    private Order market(long playerId, OrderSide side, long quantity) {
        Order order = Order.market(1L, playerId, STOCK_ID, side, quantity, 1, NOW);
        ReflectionTestUtils.setField(order, "id", nextId++);
        return order;
    }

    private static OrderBook book(Order... orders) {
        return OrderBook.of(STOCK_ID, List.of(orders));
    }

    @Test
    @DisplayName("매수 가격이 매도 가격 이상이면 체결되고, 가격이 맞지 않으면 체결되지 않는다")
    void crossCondition() {
        Order ask = limit(1, OrderSide.SELL, 10_000, 10);

        Order tooLow = limit(2, OrderSide.BUY, 9_900, 10);
        MatchResult miss = OrderMatcher.match(book(ask), tooLow, tooLow.getPrice());
        assertThat(miss.executions()).isEmpty();
        assertThat(tooLow.getStatus()).isEqualTo(OrderStatus.PENDING);

        Order equal = limit(3, OrderSide.BUY, 10_000, 10);
        MatchResult hit = OrderMatcher.match(book(ask), equal, equal.getPrice());
        assertThat(hit.executions()).hasSize(1);
        assertThat(equal.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("체결 가격은 먼저 접수되어 대기 중이던 주문의 가격이다")
    void executionPriceIsRestingPrice() {
        Order ask = limit(1, OrderSide.SELL, 10_000, 10);
        Order buy = limit(2, OrderSide.BUY, 11_000, 10);

        MatchResult result = OrderMatcher.match(book(ask), buy, buy.getPrice());

        assertThat(result.executions()).singleElement().satisfies(e -> {
            assertThat(e.price()).isEqualTo(10_000);
            assertThat(e.amount()).isEqualTo(100_000);
        });

        Order bid = limit(3, OrderSide.BUY, 11_000, 10);
        Order sell = limit(4, OrderSide.SELL, 10_000, 10);
        MatchResult sellResult = OrderMatcher.match(book(bid), sell, sell.getPrice());
        assertThat(sellResult.executions()).singleElement()
                .satisfies(e -> assertThat(e.price()).isEqualTo(11_000));
    }

    @Test
    @DisplayName("매수는 낮은 가격의 매도 주문부터, 같은 가격이면 먼저 접수된 주문부터 체결한다")
    void priorityForBuy() {
        Order late = limit(1, OrderSide.SELL, 10_000, 5);
        Order cheap = limit(2, OrderSide.SELL, 9_000, 5);
        Order early = limit(3, OrderSide.SELL, 10_000, 5);
        // late(id 1)가 early(id 3)보다 먼저 접수되었다
        Order buy = limit(4, OrderSide.BUY, 10_000, 15);

        MatchResult result = OrderMatcher.match(book(late, cheap, early), buy, buy.getPrice());

        assertThat(result.executions()).extracting(e -> e.restingOrder().getPlayerId())
                .containsExactly(2L, 1L, 3L);
    }

    @Test
    @DisplayName("매도는 높은 가격의 매수 주문부터 체결한다")
    void priorityForSell() {
        Order low = limit(1, OrderSide.BUY, 9_000, 5);
        Order high = limit(2, OrderSide.BUY, 11_000, 5);
        Order sell = limit(3, OrderSide.SELL, 9_000, 10);

        MatchResult result = OrderMatcher.match(book(low, high), sell, sell.getPrice());

        assertThat(result.executions()).extracting(Execution::price)
                .containsExactly(11_000L, 9_000L);
    }

    @Test
    @DisplayName("주문 수량 전체 또는 일부가 체결될 수 있고 미체결 지정가 잔여 수량은 대기 주문으로 유지된다")
    void partialFill() {
        Order ask = limit(1, OrderSide.SELL, 10_000, 30);
        Order buy = limit(2, OrderSide.BUY, 10_000, 50);

        MatchResult result = OrderMatcher.match(book(ask), buy, buy.getPrice());

        assertThat(result.executedQuantity()).isEqualTo(30);
        assertThat(ask.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(buy.getRemainingQuantity()).isEqualTo(20);
        assertThat(buy.isResting()).isTrue();
    }

    @Test
    @DisplayName("대기 주문의 일부만 체결되면 대기 주문은 부분 체결 상태로 남는다")
    void restingPartiallyFilled() {
        Order ask = limit(1, OrderSide.SELL, 10_000, 30);
        Order buy = limit(2, OrderSide.BUY, 10_000, 10);

        OrderMatcher.match(book(ask), buy, buy.getPrice());

        assertThat(ask.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(ask.getRemainingQuantity()).isEqualTo(20);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("같은 계정의 매수·매도 주문이 체결될 차례가 되면 신규 주문의 잔여 수량을 취소하고 기존 대기 주문은 유지한다")
    void selfTradePrevented() {
        Order mine = limit(1, OrderSide.SELL, 10_000, 10);
        Order buy = limit(1, OrderSide.BUY, 10_000, 10);

        MatchResult result = OrderMatcher.match(book(mine), buy, buy.getPrice());

        assertThat(result.selfTradePrevented()).isTrue();
        assertThat(result.executions()).isEmpty();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(mine.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(mine.getRemainingQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("자전거래 차례 이전에 이미 체결된 부분은 유지된다")
    void selfTradeKeepsEarlierFills() {
        Order other = limit(2, OrderSide.SELL, 9_000, 4);
        Order mine = limit(1, OrderSide.SELL, 10_000, 10);
        Order buy = limit(1, OrderSide.BUY, 10_000, 10);

        MatchResult result = OrderMatcher.match(book(other, mine), buy, buy.getPrice());

        assertThat(result.executedQuantity()).isEqualTo(4);
        assertThat(result.selfTradePrevented()).isTrue();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(buy.filledQuantity()).isEqualTo(4);
        assertThat(other.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(mine.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("시장가 주문은 보호 범위 안에서 체결 가능한 수량을 체결하고 잔여 수량은 취소한다")
    void marketOrderCancelsRemaining() {
        Order near = limit(1, OrderSide.SELL, 10_000, 10);
        Order far = limit(2, OrderSide.SELL, 11_000, 10);
        Order buy = market(3, OrderSide.BUY, 15);

        // 최우선 매도 호가 10,000 → 보호최고가 10,500
        MatchResult result = OrderMatcher.match(book(near, far), buy, 10_500);

        assertThat(result.executedQuantity()).isEqualTo(10);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(buy.filledQuantity()).isEqualTo(10);
        assertThat(far.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("시장가 주문의 체결 가능 수량이 0이면 전량 취소한다")
    void marketOrderNoLiquidity() {
        Order buy = market(1, OrderSide.BUY, 10);

        MatchResult result = OrderMatcher.match(book(), buy, 10_500);

        assertThat(result.executions()).isEmpty();
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(buy.filledQuantity()).isZero();
    }

    @Test
    @DisplayName("시장가 주문이 전량 체결되면 체결 완료가 된다")
    void marketOrderFullyFilled() {
        Order ask = limit(1, OrderSide.SELL, 10_000, 10);
        Order buy = market(2, OrderSide.BUY, 10);

        OrderMatcher.match(book(ask), buy, 10_500);

        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("여러 주문과 연속으로 체결되며 체결 수량은 두 주문의 잔여 수량 중 작은 쪽이다")
    void multipleExecutions() {
        List<Order> asks = new ArrayList<>();
        asks.add(limit(1, OrderSide.SELL, 10_000, 3));
        asks.add(limit(2, OrderSide.SELL, 10_000, 4));
        asks.add(limit(3, OrderSide.SELL, 10_100, 5));
        Order buy = limit(4, OrderSide.BUY, 10_100, 10);

        MatchResult result = OrderMatcher.match(
                OrderBook.of(STOCK_ID, asks), buy, buy.getPrice());

        assertThat(result.executions()).extracting(Execution::quantity)
                .containsExactly(3L, 4L, 3L);
        assertThat(asks.get(2).getRemainingQuantity()).isEqualTo(2);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("주문이 접수 순서대로 하나씩 처리되면 같은 가격에서는 먼저 접수된 주문부터 전량 체결되고 호가창은 교차하지 않는다")
    void sequentialArrivalKeepsTimePriority() {
        // 서비스는 리그 락 안에서 주문을 접수 순서(id 순서)대로 하나씩 처리한다. 이를 흉내 내어 접수할 때마다
        // 호가창을 다시 만들고 매칭한다.
        List<Order> all = new ArrayList<>();
        List<Order> asks = new ArrayList<>();
        for (Order order : List.of(
                limit(1, OrderSide.SELL, 11_000, 10),
                limit(1, OrderSide.SELL, 11_000, 10),
                limit(1, OrderSide.SELL, 11_000, 10),
                limit(2, OrderSide.BUY, 11_000, 4),
                limit(1, OrderSide.SELL, 11_000, 10),
                limit(2, OrderSide.BUY, 11_000, 12),
                limit(2, OrderSide.BUY, 11_000, 20))) {
            OrderBook book = OrderBook.of(STOCK_ID, all);
            all.add(order);
            OrderMatcher.match(book, order, order.getPrice());
            if (order.getSide() == OrderSide.SELL) {
                asks.add(order);
            }

            OrderBook after = OrderBook.of(STOCK_ID, all);
            if (after.bestBid().isPresent() && after.bestAsk().isPresent()) {
                assertThat(after.bestBid().get()).isLessThan(after.bestAsk().get());
            }
        }

        // 매도 40주 중 매수 36주가 체결: 먼저 접수된 매도 주문부터 전량 체결되고 마지막 한 건만 부분 체결
        assertThat(asks).extracting(Order::getStatus).containsExactly(
                OrderStatus.FILLED, OrderStatus.FILLED, OrderStatus.FILLED,
                OrderStatus.PARTIALLY_FILLED);
        assertThat(asks.get(3).getRemainingQuantity()).isEqualTo(4);
    }
}
