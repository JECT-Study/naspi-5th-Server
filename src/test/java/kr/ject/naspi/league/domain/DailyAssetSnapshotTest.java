package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyAssetSnapshotTest {

    @Test
    @DisplayName("거래일별 자산 집계의 거래일 번호는 1~7이다")
    void rejectsInvalidDay() {
        assertThatThrownBy(() -> DailyAssetSnapshot.of(1L, 0, 10L, 100, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DailyAssetSnapshot.of(1L, 8, 10L, 100, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(DailyAssetSnapshot.of(1L, 1, 10L, 100, 0, 100).getTradingDay()).isEqualTo(1);
        assertThat(DailyAssetSnapshot.of(1L, 7, 10L, 100, 0, 100).getTradingDay()).isEqualTo(7);
    }

    @Test
    @DisplayName("거래일별 총자산은 현금과 보유 주식 평가금액의 합이다")
    void rejectsMismatchedTotal() {
        assertThatThrownBy(() -> DailyAssetSnapshot.of(1L, 1, 10L, 50_000_000, 36_900_000,
                86_900_001))
                .isInstanceOf(IllegalArgumentException.class);

        DailyAssetSnapshot snapshot = DailyAssetSnapshot.of(1L, 1, 10L, 50_000_000, 36_900_000,
                86_900_000);
        assertThat(snapshot.getCash()).isEqualTo(50_000_000);
        assertThat(snapshot.getStockValuation()).isEqualTo(36_900_000);
        assertThat(snapshot.getTotalAsset()).isEqualTo(86_900_000);
    }

    @Test
    @DisplayName("거래일별 자산 집계 금액은 음수일 수 없다")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> DailyAssetSnapshot.of(1L, 1, 10L, -1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
