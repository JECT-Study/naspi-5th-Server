package kr.ject.naspi.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HoldingTest {

    @Test
    @DisplayName("매도 주문은 주문 수량만큼 주식을 예약하고, 매도 가능 수량은 보유 수량에서 예약 수량을 뺀 수량이다")
    void reserveForSell() {
        Holding holding = holding(1_250);

        holding.reserveForSell(400, false);

        assertThat(holding.getQuantity()).isEqualTo(1_250);
        assertThat(holding.getReservedQuantity()).isEqualTo(400);
        assertThat(holding.orderableQuantity(false)).isEqualTo(850);
    }

    @Test
    @DisplayName("매도 가능 수량 전부를 매도 주문할 수 있다")
    void reserveAll() {
        Holding holding = holding(1_250);
        holding.reserveForSell(400, false);

        holding.reserveForSell(850, false);

        assertThat(holding.getReservedQuantity()).isEqualTo(1_250);
    }

    @Test
    @DisplayName("예약 수량을 제외한 매도 가능 수량이 부족하면 매도 주문을 거절하고 예약하지 않는다")
    void rejectWhenInsufficient() {
        Holding holding = holding(1_250);
        holding.reserveForSell(400, false);

        assertThatThrownBy(() -> holding.reserveForSell(851, false))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);
        assertThat(holding.getReservedQuantity()).isEqualTo(400);
    }

    @Test
    @DisplayName("자사주는 max(0, 보유 수량 - 기존 매도 예약 수량 - 1,000주)까지 매도 주문할 수 있다")
    void reserveTreasuryStock() {
        Holding holding = holding(2_500);
        holding.reserveForSell(1_000, true);

        holding.reserveForSell(500, true);

        assertThat(holding.getReservedQuantity()).isEqualTo(1_500);
        assertThat(holding.orderableQuantity(true)).isZero();
    }

    @Test
    @DisplayName("자사주는 최소 유지분 1,000주를 남기지 못하는 매도 주문을 거절한다")
    void rejectTreasuryStockBelowMinShares() {
        Holding holding = holding(2_500);
        holding.reserveForSell(1_000, true);

        assertThatThrownBy(() -> holding.reserveForSell(501, true))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TREASURY_STOCK_MIN_SHARES);
        assertThat(holding.getReservedQuantity()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("자사주라도 보유 수량 자체를 넘는 매도 주문은 수량 부족으로 거절한다")
    void rejectTreasuryStockOverQuantity() {
        Holding holding = holding(2_500);

        assertThatThrownBy(() -> holding.reserveForSell(2_501, true))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("매도 수량은 양수여야 한다")
    void rejectNonPositive(long quantity) {
        Holding holding = holding(1_250);

        assertThatThrownBy(() -> holding.reserveForSell(quantity, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Holding holding(long quantity) {
        return Holding.allocate(1L, 10L, 100L, quantity);
    }
}
