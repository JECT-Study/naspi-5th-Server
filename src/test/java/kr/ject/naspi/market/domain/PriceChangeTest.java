package kr.ject.naspi.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceChangeTest {

    @Test
    @DisplayName("등락이 없으면 등락액 0, 등락률 0.0이다")
    void zeroChange() {
        PriceChange change = PriceChange.of(10_000, 10_000);

        assertThat(change.amount()).isZero();
        assertThat(change.rate()).isEqualTo(new BigDecimal("0.0"));
        assertThat(change.rate().scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("등락률이 정수여도 소수점 첫째 자리까지 표시한다")
    void integerRateKeepsOneDecimal() {
        assertThat(PriceChange.of(12_500, 10_000).amount()).isEqualTo(2_500);
        assertThat(PriceChange.of(12_500, 10_000).rate()).isEqualTo(new BigDecimal("25.0"));
        assertThat(PriceChange.of(13_000, 10_000).rate()).isEqualTo(new BigDecimal("30.0"));
        assertThat(PriceChange.of(7_000, 10_000).rate()).isEqualTo(new BigDecimal("-30.0"));
    }

    @Test
    @DisplayName("등락률은 소수점 둘째 자리에서 반올림한다")
    void roundsHalfUp() {
        assertThat(PriceChange.of(8_100, 8_000).rate()).isEqualTo(new BigDecimal("1.3"));
        assertThat(PriceChange.of(7_900, 8_000).rate()).isEqualTo(new BigDecimal("-1.3"));
        assertThat(PriceChange.of(8_000, 9_900).rate()).isEqualTo(new BigDecimal("-19.2"));
        assertThat(PriceChange.of(12_800, 9_900).rate()).isEqualTo(new BigDecimal("29.3"));
    }

    @Test
    @DisplayName("기준가는 0보다 커야 한다")
    void invalidBasePrice() {
        assertThatThrownBy(() -> PriceChange.of(10_000, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
