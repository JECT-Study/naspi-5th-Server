package kr.ject.naspi.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountTest {

    @Test
    @DisplayName("매수 주문은 예약 기준 가격 × 주문 수량만큼 현금을 예약하고, 주문 가능 현금은 현금에서 예약 현금을 뺀 금액이다")
    void reserveForBuy() {
        Account account = Account.open(1L, 10L, 1_000_000);

        long reserved = account.reserveForBuy(10_000, 80);

        assertThat(reserved).isEqualTo(800_000);
        assertThat(account.getDeposit()).isEqualTo(1_000_000);
        assertThat(account.getReservedDeposit()).isEqualTo(800_000);
        assertThat(account.orderableDeposit()).isEqualTo(200_000);
    }

    @Test
    @DisplayName("주문 가능 현금과 같은 금액까지는 매수 주문을 넣을 수 있다")
    void reserveAll() {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 40);

        account.reserveForBuy(10_000, 60);

        assertThat(account.getReservedDeposit()).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("예약 현금을 제외한 주문 가능 현금이 부족하면 매수 주문을 거절하고 예약하지 않는다")
    void rejectWhenInsufficient() {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        assertThatThrownBy(() -> account.reserveForBuy(10_000, 21))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_DEPOSIT);
        assertThat(account.getReservedDeposit()).isEqualTo(800_000);
    }

    @Test
    @DisplayName("가격 × 수량이 long 범위를 넘으면 현금 부족으로 거절한다")
    void rejectOverflow() {
        Account account = Account.open(1L, 10L, 1_000_000);

        assertThatThrownBy(() -> account.reserveForBuy(Long.MAX_VALUE / 2, 3))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_DEPOSIT);
        assertThat(account.getReservedDeposit()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "10000, 0", "-10000, -1"})
    @DisplayName("가격과 수량은 양수여야 한다")
    void rejectNonPositive(long price, long quantity) {
        Account account = Account.open(1L, 10L, 1_000_000);

        assertThatThrownBy(() -> account.reserveForBuy(price, quantity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("매수 주문이 취소·만료되면 미체결 잔량 × 예약 기준 가격만큼 예약 현금을 해제하고 현금은 그대로 둔다")
    void releaseForBuy() {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        long released = account.releaseForBuy(10_000, 30);

        assertThat(released).isEqualTo(300_000);
        assertThat(account.getDeposit()).isEqualTo(1_000_000);
        assertThat(account.getReservedDeposit()).isEqualTo(500_000);
        assertThat(account.orderableDeposit()).isEqualTo(500_000);
    }

    @Test
    @DisplayName("미체결 잔량 전부를 해제하면 예약 현금이 0이 된다")
    void releaseAll() {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        account.releaseForBuy(10_000, 80);

        assertThat(account.getReservedDeposit()).isZero();
    }

    @Test
    @DisplayName("예약 현금보다 많이 해제하려 하면 데이터 오류로 보고 해제하지 않는다")
    void rejectOverRelease() {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        assertThatThrownBy(() -> account.releaseForBuy(10_000, 81))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThatThrownBy(() -> account.releaseForBuy(Long.MAX_VALUE / 2, 3))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThat(account.getReservedDeposit()).isEqualTo(800_000);
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "10000, 0"})
    @DisplayName("해제할 가격과 수량은 양수여야 한다")
    void rejectNonPositiveRelease(long price, long quantity) {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        assertThatThrownBy(() -> account.releaseForBuy(price, quantity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("매수 체결은 체결 수량의 예약 현금을 풀고 실제 체결 금액만큼 현금을 줄여, 체결가가 낮으면 차액만큼 주문 가능 현금이 늘어난다")
    void settleBuyBelowReservedPrice() {
        // 10,000원 × 80주 예약 후 9,500원에 50주 체결
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        long amount = account.settleBuy(10_000, 9_500, 50);

        assertThat(amount).isEqualTo(475_000);
        assertThat(account.getDeposit()).isEqualTo(525_000);
        assertThat(account.getReservedDeposit()).isEqualTo(300_000);
        assertThat(account.orderableDeposit()).isEqualTo(225_000);
    }

    @Test
    @DisplayName("예약 기준 가격과 같은 가격에 체결되면 주문 가능 현금은 그대로다")
    void settleBuyAtReservedPrice() {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        account.settleBuy(10_000, 10_000, 80);

        assertThat(account.getDeposit()).isEqualTo(200_000);
        assertThat(account.getReservedDeposit()).isZero();
        assertThat(account.orderableDeposit()).isEqualTo(200_000);
    }

    @Test
    @DisplayName("매수 체결가가 예약 기준 가격보다 높거나 예약보다 많이 체결되면 데이터 오류로 보고 정산하지 않는다")
    void rejectInvalidBuySettlement() {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        assertThatThrownBy(() -> account.settleBuy(10_000, 10_100, 10))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThatThrownBy(() -> account.settleBuy(10_000, 10_000, 81))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThat(account.getDeposit()).isEqualTo(1_000_000);
        assertThat(account.getReservedDeposit()).isEqualTo(800_000);
    }

    @Test
    @DisplayName("매수 체결의 가격과 수량은 양수여야 한다")
    void rejectNonPositiveBuySettlement() {
        Account account = Account.open(1L, 10L, 1_000_000);
        account.reserveForBuy(10_000, 80);

        assertThatThrownBy(() -> account.settleBuy(0, 5_000, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.settleBuy(10_000, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.settleBuy(10_000, 9_500, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("매도 체결은 실제 체결 금액만큼 현금을 늘린다")
    void settleSell() {
        Account account = Account.open(1L, 10L, 1_000_000);

        long amount = account.settleSell(9_500, 50);

        assertThat(amount).isEqualTo(475_000);
        assertThat(account.getDeposit()).isEqualTo(1_475_000);
    }
}
