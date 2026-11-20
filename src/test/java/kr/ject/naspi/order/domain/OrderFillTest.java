package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderFillTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 14, 12, 0);

    private static Trade trade(long price, long quantity, LocalDateTime executedAt) {
        Order sell = order(1, 10, OrderSide.SELL, price, quantity);
        Order buy = order(2, 20, OrderSide.BUY, price, quantity);
        return Trade.record(new Execution(sell, buy, price, quantity), 1, executedAt);
    }

    private static Order order(long id, long playerId, OrderSide side, long price,
            long quantity) {
        Order order = Order.limit(1L, playerId, 100L, side, price, quantity, 1, AT);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @Test
    @DisplayName("체결 일시와 체결 단가가 모두 같은 체결은 수량과 금액을 합산해 1건으로 집계한다")
    void sameTimeAndPriceMerged() {
        List<OrderFill> fills = OrderFill.group(List.of(
                trade(10_000, 1, AT), trade(10_000, 2, AT), trade(10_000, 4, AT)));

        assertThat(fills).containsExactly(new OrderFill(AT, 10_000, 7, 70_000));
    }

    @Test
    @DisplayName("체결 단가가 같아도 체결 일시가 다르면 나누어 제공한다")
    void samePriceDifferentTimeSplit() {
        LocalDateTime later = AT.plusNanos(1_000);

        List<OrderFill> fills = OrderFill.group(List.of(
                trade(10_000, 1, AT), trade(10_000, 2, later)));

        assertThat(fills).containsExactly(new OrderFill(AT, 10_000, 1, 10_000),
                new OrderFill(later, 10_000, 2, 20_000));
    }

    @Test
    @DisplayName("체결 일시가 같아도 체결 단가가 다르면 나누어 제공하고 입력 순서를 유지한다")
    void sameTimeDifferentPriceSplit() {
        List<OrderFill> fills = OrderFill.group(List.of(
                trade(10_000, 3, AT), trade(10_100, 2, AT), trade(10_000, 1, AT)));

        assertThat(fills).containsExactly(new OrderFill(AT, 10_000, 4, 40_000),
                new OrderFill(AT, 10_100, 2, 20_200));
    }

    @Test
    @DisplayName("체결이 없으면 체결 내역은 비어 있다")
    void emptyTrades() {
        assertThat(OrderFill.group(List.of())).isEmpty();
    }
}
