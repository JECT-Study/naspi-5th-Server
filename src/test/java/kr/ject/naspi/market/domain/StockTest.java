package kr.ject.naspi.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockTest {

    private static Stock newStock() {
        return Stock.list(1L, 10L, StockName.of("나스피"), StockDescription.of("소개"));
    }

    @Test
    @DisplayName("상장 시점에는 현재가가 없고, 거래가 열리면 현재가는 시초가 10,000원이다")
    void openSetsOpeningPrice() {
        Stock stock = newStock();
        assertThat(stock.getCurrentPrice()).isNull();

        stock.open();

        assertThat(stock.getCurrentPrice()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("이미 거래가 열린 종목은 다시 열 수 없다")
    void cannotOpenTwice() {
        Stock stock = newStock();
        stock.open();

        assertThatThrownBy(stock::open).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("현재가는 최신 체결가로 갱신된다")
    void applyTradeUpdatesCurrentPrice() {
        Stock stock = newStock();
        stock.open();

        stock.applyTrade(10_500L);
        assertThat(stock.getCurrentPrice()).isEqualTo(10_500L);

        stock.applyTrade(9_800L);
        assertThat(stock.getCurrentPrice()).isEqualTo(9_800L);
    }

    @Test
    @DisplayName("거래가 열리지 않은 종목에는 체결가를 반영할 수 없다")
    void applyTradeRejectsUnopenedStock() {
        Stock stock = newStock();

        assertThatThrownBy(() -> stock.applyTrade(10_000L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stock.getCurrentPrice()).isNull();
    }

    @Test
    @DisplayName("체결가는 0보다 커야 한다")
    void applyTradeRejectsNonPositivePrice() {
        Stock stock = newStock();
        stock.open();

        assertThatThrownBy(() -> stock.applyTrade(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stock.applyTrade(-100L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stock.getCurrentPrice()).isEqualTo(10_000L);
    }
}
