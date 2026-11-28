package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class MarketOrderProtectionTest {

    // 기준가 10,000원 → 하한가 7,000원, 상한가 13,000원
    private static final long LOWER = 7_000;
    private static final long UPPER = 13_000;

    @ParameterizedTest(name = "최우선 매도 호가 {0}원 → 보호최고가 {1}원")
    @CsvSource({
            "10000, 10500",
            "10100, 10600",   // 10,605원에서 100원 미만 절사
            "10300, 10800"    // 10,815원에서 100원 미만 절사
    })
    @DisplayName("매수 보호가격은 최우선 매도 호가보다 5% 높은 가격에서 100원 미만을 절사한다")
    void buyProtection(long bestAsk, long expected) {
        assertThat(MarketOrderProtection.calculate(OrderSide.BUY, bestAsk, LOWER, UPPER))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "최우선 매수 호가 {0}원 → 보호최저가 {1}원")
    @CsvSource({
            "10000, 9500",
            "10100, 9500",    // 9,595원에서 100원 미만 절사
            "10300, 9700",    // 9,785원에서 100원 미만 절사
            "13000, 12300"    // 12,350원에서 100원 미만 절사
    })
    @DisplayName("매도 보호가격은 최우선 매수 호가보다 5% 낮은 가격에서 100원 미만을 절사한다")
    void sellProtection(long bestBid, long expected) {
        assertThat(MarketOrderProtection.calculate(OrderSide.SELL, bestBid, LOWER, UPPER))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"BUY, 12900, 13000", "BUY, 13000, 13000", "SELL, 7200, 7000", "SELL, 7000, 7000"})
    @DisplayName("산정한 보호 가격이 당일 상·하한가를 벗어나면 해당 상·하한가를 적용한다")
    void clampedToLimits(OrderSide side, long bestOpposite, long expected) {
        assertThat(MarketOrderProtection.calculate(side, bestOpposite, LOWER, UPPER))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("시장가 매수 예약금은 보호 범위 안 매도 호가를 최우선부터 주문 수량까지 합산하고, 부족한 수량은 보호최고가로 계산한다")
    void buyReservationAmount() {
        OrderBook book = asks(ask(10_000, 3), ask(10_500, 2), ask(10_600, 5));

        // 10,000 × 3 + 10,500 × 2 + 보호최고가 10,500 × 5 (10,600은 보호 범위 밖)
        assertThat(MarketOrderProtection.buyReservationAmount(book, 10, 10_500))
                .isEqualTo(30_000 + 21_000 + 52_500);
        // 주문 수량까지만 합산한다
        assertThat(MarketOrderProtection.buyReservationAmount(book, 4, 10_500))
                .isEqualTo(30_000 + 10_500);
    }

    @Test
    @DisplayName("시장가 매수 예약금이 long 범위를 넘으면 최댓값으로 계산해 주문 가능 현금 부족이 된다")
    void buyReservationAmountOverflow() {
        assertThat(MarketOrderProtection.buyReservationAmount(asks(), Long.MAX_VALUE, 10_500))
                .isEqualTo(Long.MAX_VALUE);
    }

    private long nextId = 1;

    private Order ask(long price, long quantity) {
        Order order = Order.limit(1L, 1L, 100L, OrderSide.SELL, price, quantity, 1,
                LocalDateTime.of(2026, 11, 14, 12, 0));
        ReflectionTestUtils.setField(order, "id", nextId++);
        return order;
    }

    private static OrderBook asks(Order... orders) {
        return OrderBook.of(100L, Arrays.asList(orders));
    }
}
