package kr.ject.naspi.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
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
}
