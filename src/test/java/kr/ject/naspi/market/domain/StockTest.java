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
}
