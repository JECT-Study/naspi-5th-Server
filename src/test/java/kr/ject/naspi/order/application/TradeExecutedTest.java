package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.ject.naspi.order.domain.Execution;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TradeExecutedTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);

    @Test
    @DisplayName("체결 이벤트는 매수·매도 주문과 플레이어를 구분하고 체결가와 수량을 담는다")
    void fromExecution() {
        Order sell = order(1L, 10L, OrderSide.SELL);
        Order buy = order(2L, 20L, OrderSide.BUY);
        Execution execution = new Execution(sell, buy, 12_300, 7);

        TradeExecuted event = TradeExecuted.from(execution, NOW);

        assertThat(event.leagueId()).isEqualTo(1);
        assertThat(event.stockId()).isEqualTo(5);
        assertThat(event.buyOrderId()).isEqualTo(2);
        assertThat(event.sellOrderId()).isEqualTo(1);
        assertThat(event.buyerPlayerId()).isEqualTo(20);
        assertThat(event.sellerPlayerId()).isEqualTo(10);
        assertThat(event.price()).isEqualTo(12_300);
        assertThat(event.quantity()).isEqualTo(7);
        assertThat(event.executedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("대기 중이던 주문이 매도면 매도자가 대기 주문 주인이다")
    void restingSell() {
        Execution execution = new Execution(order(1L, 10L, OrderSide.SELL),
                order(2L, 20L, OrderSide.BUY), 12_300, 7);

        TradeExecuted event = TradeExecuted.from(execution, NOW);

        assertThat(event.buyResting()).isFalse();
        assertThat(event.restingPlayerId()).isEqualTo(10);
    }

    @Test
    @DisplayName("대기 중이던 주문이 매수면 매수자가 대기 주문 주인이다")
    void restingBuy() {
        Execution execution = new Execution(order(2L, 20L, OrderSide.BUY),
                order(1L, 10L, OrderSide.SELL), 12_300, 7);

        TradeExecuted event = TradeExecuted.from(execution, NOW);

        assertThat(event.buyResting()).isTrue();
        assertThat(event.restingPlayerId()).isEqualTo(20);
    }

    private static Order order(long id, long playerId, OrderSide side) {
        Order order = Order.limit(1, playerId, 5, side, 12_300, 10, 1, NOW);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
