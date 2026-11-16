package kr.ject.naspi.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceLimitTest {

    @Test
    @DisplayName("기준가 10,000원이면 제한폭 3,000원, 상한 13,000원, 하한 7,000원")
    void baseTenThousand() {
        PriceLimit limit = PriceLimit.of(10_000);

        assertThat(limit.upper()).isEqualTo(13_000);
        assertThat(limit.lower()).isEqualTo(7_000);
    }

    @Test
    @DisplayName("제한폭 금액은 기준가의 30%에서 100원 미만을 절사한다 (12,300원 → 3,690원 → 3,600원)")
    void amountIsTruncatedToTick() {
        PriceLimit limit = PriceLimit.of(12_300);

        assertThat(limit.upper()).isEqualTo(15_900);
        assertThat(limit.lower()).isEqualTo(8_700);
    }

    @Test
    @DisplayName("기준가가 100원이면 제한폭이 0원이라 상·하한가가 기준가와 같다")
    void minimumBase() {
        PriceLimit limit = PriceLimit.of(100);

        assertThat(limit.lower()).isEqualTo(100);
        assertThat(limit.upper()).isEqualTo(100);
    }

    @Test
    @DisplayName("상·하한가는 항상 100원 단위다")
    void resultsAreTickAligned() {
        for (long base = 100; base <= 100_000; base += 100) {
            PriceLimit limit = PriceLimit.of(base);

            assertThat(limit.lower() % PriceLimit.TICK_SIZE).isZero();
            assertThat(limit.upper() % PriceLimit.TICK_SIZE).isZero();
        }
    }

    @Test
    @DisplayName("같은 기준가는 항상 같은 결과를 낸다")
    void deterministic() {
        assertThat(PriceLimit.of(12_300)).isEqualTo(PriceLimit.of(12_300));
    }

    @Test
    @DisplayName("상한가·하한가와 같은 가격은 범위 안이고, 1틱 벗어나면 범위 밖이다")
    void boundaryIsInclusive() {
        PriceLimit limit = PriceLimit.of(10_000);

        assertThat(limit.contains(13_000)).isTrue();
        assertThat(limit.contains(7_000)).isTrue();
        assertThat(limit.contains(13_100)).isFalse();
        assertThat(limit.contains(6_900)).isFalse();
    }

    @Test
    @DisplayName("범위를 벗어난 가격은 상한가 또는 하한가로 맞춘다")
    void clampToLimit() {
        PriceLimit limit = PriceLimit.of(10_000);

        assertThat(limit.clamp(14_000)).isEqualTo(13_000);
        assertThat(limit.clamp(6_000)).isEqualTo(7_000);
        assertThat(limit.clamp(10_500)).isEqualTo(10_500);
    }

    @Test
    @DisplayName("기준가가 100원 미만이거나 100원 단위가 아니면 계산할 수 없다")
    void invalidBase() {
        assertThatThrownBy(() -> PriceLimit.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriceLimit.of(50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriceLimit.of(10_050))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
