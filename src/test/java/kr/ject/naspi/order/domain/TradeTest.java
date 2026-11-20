package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
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

    private Trade trade(long price, long quantity) {
        Execution execution = new Execution(order(1, 10, OrderSide.SELL, price, quantity),
                order(2, 20, OrderSide.BUY, price, quantity), price, quantity);
        return Trade.record(execution, 2, NOW);
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

    @Test
    @DisplayName("종료에 가까운 체결부터 500주까지 모으고 마지막 체결 건은 500주를 채우는 데 필요한 수량만 반영한다")
    void takesRecentQuantityUntilLimit() {
        List<Trade> recentFirst = List.of(trade(10_000, 300), trade(11_000, 150),
                trade(12_000, 100), trade(13_000, 50));

        List<TradeSlice> slices = Trade.takeRecentQuantity(recentFirst, 500);

        assertThat(slices).containsExactly(new TradeSlice(10_000, 300),
                new TradeSlice(11_000, 150), new TradeSlice(12_000, 50));
    }

    @Test
    @DisplayName("전체 체결 수량이 500주 미만이면 전체 체결을 사용한다")
    void usesAllTradesBelowLimit() {
        List<Trade> recentFirst = List.of(trade(10_000, 100), trade(11_000, 200));

        assertThat(Trade.takeRecentQuantity(recentFirst, 500)).containsExactly(
                new TradeSlice(10_000, 100), new TradeSlice(11_000, 200));
        assertThat(Trade.takeRecentQuantity(List.of(), 500)).isEmpty();
    }
}
