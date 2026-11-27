package kr.ject.naspi.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TradeVolumeTest {

    @Test
    @DisplayName("종료 평가 가격은 Σ(체결가×수량)/Σ수량이며 원 미만은 절사하고 100원 호가 단위로 맞추지 않는다")
    void vwapFloorsBelowWon() {
        TradeVolume volume = TradeVolume.of(List.of(new EvaluatedTrade(10_000, 1),
                new EvaluatedTrade(10_100, 2)));

        assertThat(volume).isEqualTo(new TradeVolume(3, 30_200));
        assertThat(volume.volumeWeightedAveragePrice()).isEqualTo(10_066);
    }

    @Test
    @DisplayName("마지막 체결 건은 반영한 수량만 금액에 포함한다")
    void ofSumsPartialQuantity() {
        List<EvaluatedTrade> taken = FinalEvaluationPrice.takeRecentQuantity(List.of(
                new EvaluatedTrade(10_000, 300), new EvaluatedTrade(11_000, 150),
                new EvaluatedTrade(12_000, 100)), 500);

        TradeVolume volume = TradeVolume.of(taken);

        assertThat(volume).isEqualTo(new TradeVolume(500, 5_250_000));
        assertThat(volume.volumeWeightedAveragePrice()).isEqualTo(10_500);
    }

    @Test
    @DisplayName("체결 수량이 0이면 평균가를 구할 수 없다")
    void vwapRejectsZeroQuantity() {
        assertThatThrownBy(() -> new TradeVolume(0, 0).volumeWeightedAveragePrice())
                .isInstanceOf(IllegalStateException.class);
    }
}
