package kr.ject.naspi.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
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

    @Test
    @DisplayName("매도 주문이 취소·만료되면 미체결 잔량만큼 예약 수량을 해제하고 보유 수량은 그대로 둔다")
    void releaseForSell() {
        Holding holding = holding(1_250);
        holding.reserveForSell(400, false);

        holding.releaseForSell(150);

        assertThat(holding.getQuantity()).isEqualTo(1_250);
        assertThat(holding.getReservedQuantity()).isEqualTo(250);
        assertThat(holding.orderableQuantity(false)).isEqualTo(1_000);
    }

    @Test
    @DisplayName("자사주 매도 주문을 해제하면 그만큼 다시 매도할 수 있다")
    void releaseTreasuryStock() {
        Holding holding = holding(2_500);
        holding.reserveForSell(1_500, true);

        holding.releaseForSell(1_500);

        assertThat(holding.orderableQuantity(true)).isEqualTo(1_500);
    }

    @Test
    @DisplayName("예약 수량보다 많이 해제하려 하면 데이터 오류로 보고 해제하지 않는다")
    void rejectOverRelease() {
        Holding holding = holding(1_250);
        holding.reserveForSell(400, false);

        assertThatThrownBy(() -> holding.releaseForSell(401))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThat(holding.getReservedQuantity()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("해제 수량은 양수여야 한다")
    void rejectNonPositiveRelease(long quantity) {
        Holding holding = holding(1_250);
        holding.reserveForSell(400, false);

        assertThatThrownBy(() -> holding.releaseForSell(quantity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("매수 체결은 보유 수량을 늘리고 매입 금액에 체결가 × 체결 수량을 더한다 (이동평균법)")
    void settleBuy() {
        Holding holding = holding(1_250);

        holding.settleBuy(12_000, 250);

        assertThat(holding.getQuantity()).isEqualTo(1_500);
        assertThat(holding.getPurchaseAmount()).isEqualTo(12_500_000 + 3_000_000);
    }

    @Test
    @DisplayName("매도 체결은 보유·예약 수량을 함께 줄이고 매입 금액을 매도 후 수량 비율로 줄이며 원 미만은 절사한다")
    void settleSell() {
        // 매입 금액 40,001원, 4주 → 1주 매도 → 40,001 × 3 ÷ 4 = 30,000.75 → 30,000원
        Holding holding = holding(3);
        holding.settleBuy(10_001, 1);
        holding.reserveForSell(1, false);

        holding.settleSell(1);

        assertThat(holding.getQuantity()).isEqualTo(3);
        assertThat(holding.getReservedQuantity()).isZero();
        assertThat(holding.getPurchaseAmount()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("전량 매도하면 매입 금액은 0이 되고, 이후 매수는 0부터 다시 계산한다")
    void settleSellAllThenBuy() {
        Holding holding = holding(1_250);
        holding.reserveForSell(1_250, false);

        holding.settleSell(1_250);

        assertThat(holding.getQuantity()).isZero();
        assertThat(holding.getPurchaseAmount()).isZero();

        holding.settleBuy(8_000, 100);

        assertThat(holding.getPurchaseAmount()).isEqualTo(800_000);
    }

    @Test
    @DisplayName("자사주는 접수 시 남긴 최소 유지분 1,000주가 매도 체결 후에도 유지된다")
    void treasuryStockMinSharesKeptAfterSettlement() {
        Holding holding = holding(2_500);
        holding.reserveForSell(1_500, true);

        holding.settleSell(1_500);

        assertThat(holding.getQuantity()).isEqualTo(1_000);
        assertThat(holding.orderableQuantity(true)).isZero();
    }

    @Test
    @DisplayName("매도 체결 수량이 예약 수량을 넘으면 데이터 오류로 보고 정산하지 않는다")
    void rejectSellSettlementOverReserved() {
        Holding holding = holding(2_500);
        holding.reserveForSell(1_500, true);

        assertThatThrownBy(() -> holding.settleSell(1_501))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThat(holding.getQuantity()).isEqualTo(2_500);
        assertThat(holding.getReservedQuantity()).isEqualTo(1_500);
    }

    private static Holding holding(long quantity) {
        return Holding.allocate(1L, 10L, 100L, quantity);
    }
}
