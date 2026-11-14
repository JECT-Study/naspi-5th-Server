package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 정산 규칙 자체는 {@code AccountTest}, {@code HoldingTest}가 검증한다. 여기서는 매수자·매도자를 함께 정산해
 * 저장하는지 확인한다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(AssetSettlementService.class)
class AssetSettlementServiceTest {

    private static final long LEAGUE_ID = 1L;
    private static final long STOCK_ID = 100L;

    @Autowired
    private AssetSettlementService service;

    @Autowired
    private TestEntityManager entityManager;

    @ParameterizedTest(name = "매수자 {0}, 매도자 {1}")
    @CsvSource({"10, 11", "11, 10"})
    @DisplayName("체결 1건으로 매수자는 현금 감소·주식 증가, 매도자는 주식 감소·현금 증가로 함께 정산된다")
    void settle(long buyerId, long sellerId) {
        // 매수자: 10,000원 × 80주 예약 / 매도자: 1,250주 중 100주 매도 예약 → 9,500원에 50주 체결
        Account buyerAccount = Account.open(LEAGUE_ID, buyerId, 1_000_000);
        buyerAccount.reserveForBuy(10_000, 80);
        Holding buyerHolding = Holding.allocate(LEAGUE_ID, buyerId, STOCK_ID, 1_250);
        Account sellerAccount = Account.open(LEAGUE_ID, sellerId, 1_000_000);
        Holding sellerHolding = Holding.allocate(LEAGUE_ID, sellerId, STOCK_ID, 1_250);
        sellerHolding.reserveForSell(100, false);
        persistAll(buyerAccount, buyerHolding, sellerAccount, sellerHolding);

        service.settle(new TradeSettlement(LEAGUE_ID, STOCK_ID, buyerId, sellerId, 10_000, 9_500,
                50));

        entityManager.flush();
        entityManager.clear();
        Account buyerAfter = entityManager.find(Account.class, buyerAccount.getId());
        assertThat(buyerAfter.getDeposit()).isEqualTo(525_000);
        assertThat(buyerAfter.getReservedDeposit()).isEqualTo(300_000);
        Holding buyerHoldingAfter = entityManager.find(Holding.class, buyerHolding.getId());
        assertThat(buyerHoldingAfter.getQuantity()).isEqualTo(1_300);
        assertThat(buyerHoldingAfter.getPurchaseAmount()).isEqualTo(12_500_000 + 475_000);

        Account sellerAfter = entityManager.find(Account.class, sellerAccount.getId());
        assertThat(sellerAfter.getDeposit()).isEqualTo(1_475_000);
        Holding sellerHoldingAfter = entityManager.find(Holding.class, sellerHolding.getId());
        assertThat(sellerHoldingAfter.getQuantity()).isEqualTo(1_200);
        assertThat(sellerHoldingAfter.getReservedQuantity()).isEqualTo(50);
        assertThat(sellerHoldingAfter.getPurchaseAmount()).isEqualTo(12_000_000);
    }

    @Test
    @DisplayName("매수자와 매도자가 같은 체결은 정산하지 않는다 (자전거래 금지)")
    void rejectSelfTrade() {
        assertThatThrownBy(() -> service.settle(
                new TradeSettlement(LEAGUE_ID, STOCK_ID, 10L, 10L, 10_000, 9_500, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정산할 계좌나 보유 주식이 없으면 데이터 오류로 본다")
    void rejectWithoutAsset() {
        assertThatThrownBy(() -> service.settle(
                new TradeSettlement(LEAGUE_ID, STOCK_ID, 10L, 11L, 10_000, 9_500, 1)))
                .isInstanceOf(NaspiRuntimeException.class);
    }

    private void persistAll(Object... entities) {
        for (Object entity : entities) {
            entityManager.persist(entity);
        }
        entityManager.flush();
    }
}
