package kr.ject.naspi.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockDailyPriceTest {

    @Test
    @DisplayName("거래일의 거래량은 0부터 시작한다")
    void startsWithZeroVolume() {
        StockDailyPrice dailyPrice = StockDailyPrice.open(1L, 10L, 1, 10_000);

        assertThat(dailyPrice.getLeagueId()).isEqualTo(1L);
        assertThat(dailyPrice.getStockId()).isEqualTo(10L);
        assertThat(dailyPrice.getTradingDay()).isEqualTo(1);
        assertThat(dailyPrice.getVolume()).isZero();
    }

    @Test
    @DisplayName("거래량은 실제 체결 수량의 합계다")
    void addVolumeSumsQuantity() {
        StockDailyPrice dailyPrice = StockDailyPrice.open(1L, 10L, 1, 10_000);

        dailyPrice.addVolume(3);
        dailyPrice.addVolume(1);
        dailyPrice.addVolume(5);

        assertThat(dailyPrice.getVolume()).isEqualTo(9);
    }

    @Test
    @DisplayName("체결 수량은 1주 이상이어야 거래량에 더할 수 있다")
    void addVolumeRejectsNonPositive() {
        StockDailyPrice dailyPrice = StockDailyPrice.open(1L, 10L, 1, 10_000);

        assertThatThrownBy(() -> dailyPrice.addVolume(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dailyPrice.addVolume(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dailyPrice.getVolume()).isZero();
    }

    @Test
    @DisplayName("거래일 번호는 1~7일이다")
    void tradingDayRange() {
        assertThatThrownBy(() -> StockDailyPrice.open(1L, 10L, 0, 10_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockDailyPrice.open(1L, 10L, 8, 10_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(StockDailyPrice.open(1L, 10L, 1, 10_000).getTradingDay()).isEqualTo(1);
        assertThat(StockDailyPrice.open(1L, 10L, 7, 10_000).getTradingDay()).isEqualTo(7);
    }

    @Test
    @DisplayName("새 거래일의 기준가로 상·하한가가 정해지고 거래량은 0부터 시작한다")
    void openSetsBaseAndLimits() {
        StockDailyPrice first = StockDailyPrice.open(1L, 10L, 1, 10_000);
        StockDailyPrice rounded = StockDailyPrice.open(1L, 10L, 2, 12_300);

        assertThat(first.getBasePrice()).isEqualTo(10_000);
        assertThat(first.getLowerLimitPrice()).isEqualTo(7_000);
        assertThat(first.getUpperLimitPrice()).isEqualTo(13_000);
        assertThat(first.getVolume()).isZero();
        assertThat(first.getClosePrice()).isNull();
        assertThat(rounded.getLowerLimitPrice()).isEqualTo(8_700);
        assertThat(rounded.getUpperLimitPrice()).isEqualTo(15_900);
        assertThat(rounded.priceLimit().contains(15_900)).isTrue();
        assertThat(rounded.priceLimit().contains(16_000)).isFalse();
    }

    @Test
    @DisplayName("종가는 거래일의 마지막 체결가다")
    void closeUsesLastTradePrice() {
        StockDailyPrice dailyPrice = StockDailyPrice.open(1L, 10L, 1, 10_000);
        dailyPrice.addVolume(5);

        assertThat(dailyPrice.close(10_500)).isEqualTo(10_500);
        assertThat(dailyPrice.getClosePrice()).isEqualTo(10_500);
        assertThat(dailyPrice.isClosed()).isTrue();
    }

    @Test
    @DisplayName("거래량이 0인 거래일의 종가는 기준가다")
    void closeUsesBasePriceWhenNoVolume() {
        StockDailyPrice dailyPrice = StockDailyPrice.open(1L, 10L, 1, 10_000);

        assertThat(dailyPrice.close(12_000)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("이미 마감한 거래일은 다시 마감할 수 없다")
    void closeTwiceRejected() {
        StockDailyPrice dailyPrice = StockDailyPrice.open(1L, 10L, 1, 10_000);
        dailyPrice.close(10_000);

        assertThatThrownBy(() -> dailyPrice.close(10_000))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("마감한 거래일에는 체결 수량을 더할 수 없다")
    void addVolumeAfterCloseRejected() {
        StockDailyPrice dailyPrice = StockDailyPrice.open(1L, 10L, 1, 10_000);
        dailyPrice.close(10_000);

        assertThatThrownBy(() -> dailyPrice.addVolume(1))
                .isInstanceOf(IllegalStateException.class);
    }
}
