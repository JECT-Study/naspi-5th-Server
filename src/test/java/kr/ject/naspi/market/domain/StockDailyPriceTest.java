package kr.ject.naspi.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockDailyPriceTest {

    @Test
    @DisplayName("거래일의 거래량은 0부터 시작한다")
    void startsWithZeroVolume() {
        StockDailyPrice dailyPrice = StockDailyPrice.start(1L, 10L, 1);

        assertThat(dailyPrice.getLeagueId()).isEqualTo(1L);
        assertThat(dailyPrice.getStockId()).isEqualTo(10L);
        assertThat(dailyPrice.getTradingDay()).isEqualTo(1);
        assertThat(dailyPrice.getVolume()).isZero();
    }

    @Test
    @DisplayName("거래량은 실제 체결 수량의 합계다")
    void addVolumeSumsQuantity() {
        StockDailyPrice dailyPrice = StockDailyPrice.start(1L, 10L, 1);

        dailyPrice.addVolume(3);
        dailyPrice.addVolume(1);
        dailyPrice.addVolume(5);

        assertThat(dailyPrice.getVolume()).isEqualTo(9);
    }

    @Test
    @DisplayName("체결 수량은 1주 이상이어야 거래량에 더할 수 있다")
    void addVolumeRejectsNonPositive() {
        StockDailyPrice dailyPrice = StockDailyPrice.start(1L, 10L, 1);

        assertThatThrownBy(() -> dailyPrice.addVolume(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dailyPrice.addVolume(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dailyPrice.getVolume()).isZero();
    }

    @Test
    @DisplayName("거래일 번호는 1~7일이다")
    void tradingDayRange() {
        assertThatThrownBy(() -> StockDailyPrice.start(1L, 10L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockDailyPrice.start(1L, 10L, 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(StockDailyPrice.start(1L, 10L, 1).getTradingDay()).isEqualTo(1);
        assertThat(StockDailyPrice.start(1L, 10L, 7).getTradingDay()).isEqualTo(7);
    }
}
