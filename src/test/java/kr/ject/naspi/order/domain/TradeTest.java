package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TradeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);

    private Order order(long id, long playerId, OrderSide side, long price, long quantity) {
        Order order = Order.limit(1L, playerId, 100L, side, price, quantity, 2, NOW);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @Test
    @DisplayName("체결 기록은 매수·매도 주문과 플레이어, 체결가·수량·금액(가격 × 수량)·일시를 담는다")
    void recordsExecution() {
        Execution execution = new Execution(order(1, 10, OrderSide.SELL, 11_000, 10),
                order(2, 20, OrderSide.BUY, 12_000, 4), 11_000, 4);

        Trade trade = Trade.record(execution, 2, NOW);

        assertThat(trade.getLeagueId()).isEqualTo(1L);
        assertThat(trade.getStockId()).isEqualTo(100L);
        assertThat(trade.getTradingDay()).isEqualTo(2);
        assertThat(trade.getBuyOrderId()).isEqualTo(2L);
        assertThat(trade.getSellOrderId()).isEqualTo(1L);
        assertThat(trade.getBuyerPlayerId()).isEqualTo(20L);
        assertThat(trade.getSellerPlayerId()).isEqualTo(10L);
        assertThat(trade.getPrice()).isEqualTo(11_000);
        assertThat(trade.getQuantity()).isEqualTo(4);
        assertThat(trade.getAmount()).isEqualTo(44_000);
        assertThat(trade.getExecutedAt()).isEqualTo(NOW);
    }
}
