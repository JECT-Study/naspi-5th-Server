package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);

    private static Order limit(OrderSide side, long price, long quantity) {
        return Order.limit(1L, 10L, 100L, side, price, quantity, 1, NOW);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 50, 99, 150, 10_050, -100})
    @DisplayName("주문 가격은 100원 이상의 100원 단위여야 한다")
    void invalidPrice(long price) {
        assertThatThrownBy(() -> limit(OrderSide.BUY, price, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting(e -> ((NaspiBizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_PRICE);
    }

    @ParameterizedTest
    @ValueSource(longs = {100, 10_000, 13_000})
    @DisplayName("100원 이상의 100원 단위 가격은 접수할 수 있다")
    void validPrice(long price) {
        assertThat(limit(OrderSide.BUY, price, 1).getPrice()).isEqualTo(price);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("주문 수량은 1주 단위의 양의 정수여야 한다")
    void invalidQuantity(long quantity) {
        assertThatThrownBy(() -> limit(OrderSide.SELL, 10_000, quantity))
                .isInstanceOf(NaspiBizException.class)
                .extracting(e -> ((NaspiBizException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_QUANTITY);
        assertThatThrownBy(() -> Order.market(1L, 10L, 100L, OrderSide.BUY, quantity, 1, NOW))
                .isInstanceOf(NaspiBizException.class);
    }

    @Test
    @DisplayName("새 주문은 대기 상태이고 잔여 수량은 주문 수량과 같다")
    void newOrderIsPending() {
        Order order = limit(OrderSide.BUY, 10_000, 30);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getRemainingQuantity()).isEqualTo(30);
        assertThat(order.filledQuantity()).isZero();
        assertThat(order.isResting()).isTrue();
    }

    @Test
    @DisplayName("시장가 주문은 가격이 없고 호가창에 남지 않는다")
    void marketOrder() {
        Order order = Order.market(1L, 10L, 100L, OrderSide.BUY, 5, 1, NOW);

        assertThat(order.getType()).isEqualTo(OrderType.MARKET);
        assertThat(order.getPrice()).isNull();
        assertThat(order.isResting()).isFalse();
    }

    @Test
    @DisplayName("일부 체결되면 부분 체결, 전량 체결되면 체결 완료가 되고 이미 체결된 수량은 유지된다")
    void fill() {
        Order order = limit(OrderSide.SELL, 10_000, 30);

        order.fill(10);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(order.getRemainingQuantity()).isEqualTo(20);
        assertThat(order.isResting()).isTrue();

        order.fill(20);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getRemainingQuantity()).isZero();
        assertThat(order.filledQuantity()).isEqualTo(30);
        assertThat(order.isResting()).isFalse();
    }

    @Test
    @DisplayName("잔여 수량을 넘는 수량은 체결할 수 없다")
    void fillOverRemaining() {
        Order order = limit(OrderSide.SELL, 10_000, 30);

        assertThatThrownBy(() -> order.fill(31)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.fill(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("부분 체결된 주문을 취소하면 체결된 부분은 유지하고 잔여 수량만 취소된다")
    void cancelPartiallyFilled() {
        Order order = limit(OrderSide.BUY, 10_000, 30);
        order.fill(10);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.filledQuantity()).isEqualTo(10);
        assertThat(order.isResting()).isFalse();
    }

    @Test
    @DisplayName("지정가 주문의 미체결 잔여 수량은 만료될 수 있다")
    void expire() {
        Order order = limit(OrderSide.BUY, 10_000, 30);

        order.expire();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("체결 완료·취소·만료된 주문은 더 이상 상태를 바꿀 수 없다")
    void terminalStatesAreFinal() {
        Order filled = limit(OrderSide.BUY, 10_000, 1);
        filled.fill(1);
        Order canceled = limit(OrderSide.BUY, 10_000, 1);
        canceled.cancel();
        Order expired = limit(OrderSide.BUY, 10_000, 1);
        expired.expire();

        for (Order order : new Order[] {filled, canceled, expired}) {
            assertThatThrownBy(order::cancel).isInstanceOf(NaspiBizException.class);
            assertThatThrownBy(order::expire).isInstanceOf(NaspiBizException.class);
            assertThatThrownBy(() -> order.fill(1)).isInstanceOf(NaspiBizException.class);
        }
    }

    @Test
    @DisplayName("매수 주문의 예약 현금은 예약 기준 가격 × 미체결 잔여 수량이다")
    void reservedCash() {
        Order order = limit(OrderSide.BUY, 10_000, 30);
        assertThat(order.reservedCash()).isZero();

        order.recordReservedPrice(10_000);
        assertThat(order.reservedCash()).isEqualTo(300_000);

        order.fill(10);
        assertThat(order.reservedCash()).isEqualTo(200_000);
    }

    @Test
    @DisplayName("예약 기준 가격은 매수 주문에만 기록할 수 있고 양수여야 한다")
    void reservedPriceRules() {
        Order sell = limit(OrderSide.SELL, 10_000, 30);
        Order buy = limit(OrderSide.BUY, 10_000, 30);

        assertThatThrownBy(() -> sell.recordReservedPrice(10_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> buy.recordReservedPrice(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sell.reservedCash()).isZero();
    }
}
