package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.AccountRepository;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.asset.domain.HoldingRepository;
import kr.ject.naspi.asset.domain.StockListing;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import(InitialAssetAllocationService.class)
class InitialAssetAllocationServiceTest {

    private static final long LEAGUE_ID = 1L;

    /** 참여자 4명: 다른 참여자에게 833주씩, 상장자에게 2,500 + 잔여 1주. */
    private static final List<StockListing> LISTINGS = List.of(
            new StockListing(101, 1), new StockListing(102, 2),
            new StockListing(103, 3), new StockListing(104, 4));

    @Autowired
    private InitialAssetAllocationService service;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Test
    @DisplayName("리그가 시작되면 모든 참여자에게 계좌와 초기 현금을 배분한다")
    void allocatesAccounts() {
        service.allocate(LEAGUE_ID, LISTINGS);

        // 보유 주식 2,501 + 833 × 3 = 5,000주 → 현금 1억 - 5,000 × 10,000 = 5,000만 원
        assertThat(accountRepository.findAllByLeagueId(LEAGUE_ID))
                .extracting(Account::getPlayerId, Account::getDeposit,
                        Account::getReservedDeposit)
                .containsExactlyInAnyOrder(
                        tuple(1L, 50_000_000L, 0L), tuple(2L, 50_000_000L, 0L),
                        tuple(3L, 50_000_000L, 0L), tuple(4L, 50_000_000L, 0L));
    }

    @Test
    @DisplayName("리그가 시작되면 모든 참여자에게 모든 종목의 주식을 배분한다")
    void allocatesHoldings() {
        service.allocate(LEAGUE_ID, LISTINGS);

        List<Holding> holdings = holdingRepository.findAllByLeagueId(LEAGUE_ID);
        assertThat(holdings).hasSize(16)
                .allSatisfy(holding -> {
                    assertThat(holding.getReservedQuantity()).isZero();
                    assertThat(holding.isInherited()).isFalse();
                });
        assertThat(holdings)
                .filteredOn(holding -> holding.getStockId() == 101L)
                .extracting(Holding::getPlayerId, Holding::getQuantity)
                .containsExactlyInAnyOrder(
                        tuple(1L, 2_501L), tuple(2L, 833L), tuple(3L, 833L), tuple(4L, 833L));
    }

    @Test
    @DisplayName("초기 배분 주식의 매입 금액은 배분 수량 × 시초가 10,000원이다")
    void allocatesPurchaseAmount() {
        service.allocate(LEAGUE_ID, LISTINGS);

        assertThat(holdingRepository.findAllByLeagueId(LEAGUE_ID))
                .allSatisfy(holding -> assertThat(holding.getPurchaseAmount())
                        .isEqualTo(holding.getQuantity() * 10_000));
    }

    @Test
    @DisplayName("이미 초기 자산이 배분된 리그에는 다시 배분하지 않는다")
    void rejectsDuplicateAllocation() {
        service.allocate(LEAGUE_ID, LISTINGS);

        assertThatThrownBy(() -> service.allocate(LEAGUE_ID, LISTINGS))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThat(accountRepository.findAllByLeagueId(LEAGUE_ID)).hasSize(4);
        assertThat(holdingRepository.findAllByLeagueId(LEAGUE_ID)).hasSize(16);
    }

    @Test
    @DisplayName("배분 조건에 맞지 않으면 어떤 참여자에게도 자산을 배분하지 않는다")
    void allocatesNothingOnInvalidListings() {
        List<StockListing> invalid = List.of(
                new StockListing(101, 1), new StockListing(102, 1), new StockListing(103, 3));

        assertThatThrownBy(() -> service.allocate(LEAGUE_ID, invalid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accountRepository.existsByLeagueId(LEAGUE_ID)).isFalse();
        assertThat(holdingRepository.findAllByLeagueId(LEAGUE_ID)).isEmpty();
    }
}
