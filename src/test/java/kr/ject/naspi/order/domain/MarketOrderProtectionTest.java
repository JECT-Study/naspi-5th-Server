package kr.ject.naspi.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
}
