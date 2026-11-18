package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.asset.domain.InitialAllocation;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.MemberPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@DataJpaTest
@Import(AssetWithdrawalService.class)
class AssetWithdrawalServiceTest {

    private static final long LEAGUE_ID = 1L;

    @Autowired
    private AssetWithdrawalService service;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @Test
    @DisplayName("탈퇴하면 현금은 폐기되고 모든 종목의 보유 주식은 탈퇴 시점 수량 그대로 유지된다")
    void discardCashKeepsHoldings() {
        final Account account = entityManager.persist(Account.open(LEAGUE_ID, 10L, 1_000_000));
        final Holding own = entityManager.persist(Holding.allocate(LEAGUE_ID, 10L, 100L, 2_500));
        final Holding other = entityManager.persist(Holding.allocate(LEAGUE_ID, 10L, 101L, 625));

        service.discardCash(LEAGUE_ID, 10L);
        entityManager.flush();
        entityManager.clear();

        Account reloaded = entityManager.find(Account.class, account.getId());
        assertThat(reloaded.getDeposit()).isZero();
        assertThat(reloaded.isWithdrawn()).isTrue();
        Holding reloadedOwn = entityManager.find(Holding.class, own.getId());
        assertThat(reloadedOwn.getQuantity()).isEqualTo(2_500);
        assertThat(entityManager.find(Holding.class, other.getId()).getQuantity()).isEqualTo(625);
    }

    @Test
    @DisplayName("미체결 매도 주문에 묶인 주식이 남아 있으면 현금을 폐기하지 않는다 (미체결 주문 취소가 먼저)")
    void rejectDiscardWithReservedStock() {
        final Account account = entityManager.persist(Account.open(LEAGUE_ID, 10L, 1_000_000));
        final Holding holding = entityManager.persist(
                Holding.allocate(LEAGUE_ID, 10L, 100L, 2_500));
        holding.reserveForSell(500, true);

        assertThatThrownBy(() -> service.discardCash(LEAGUE_ID, 10L))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThat(account.getDeposit()).isEqualTo(1_000_000);
        assertThat(account.isWithdrawn()).isFalse();
        assertThat(holding.getReservedQuantity()).isEqualTo(500);
    }

    @Test
    @DisplayName("탈퇴자의 보유 주식은 각 종목의 발행주식 총수에 포함된다")
    void issuedSharesIncludeWithdrawnHoldings() {
        entityManager.persist(Account.open(LEAGUE_ID, 10L, 1_000_000));
        entityManager.persist(Holding.allocate(LEAGUE_ID, 10L, 100L, 2_500));
        entityManager.persist(Holding.allocate(LEAGUE_ID, 11L, 100L, 1_250));
        entityManager.persist(Holding.allocate(LEAGUE_ID, 12L, 100L, 1_250));

        service.discardCash(LEAGUE_ID, 10L);
        entityManager.flush();
        entityManager.clear();

        long total = entityManager.getEntityManager()
                .createQuery("select sum(h.quantity) from Holding h where h.stockId = 100",
                        Long.class)
                .getSingleResult();
        assertThat(total).isEqualTo(InitialAllocation.ISSUED_SHARES);
    }

    @Test
    @DisplayName("탈퇴 회원이 참여한 모든 리그의 현금이 폐기된다")
    void discardAllLeaguesOfMember() {
        final Account first = entityManager.persist(Account.open(1L, 10L, 1_000_000));
        final Account second = entityManager.persist(Account.open(2L, 20L, 500_000));
        final Account other = entityManager.persist(Account.open(1L, 11L, 700_000));
        BDDMockito.given(leagueQueryService.findStartedPlayersOfMember(7L))
                .willReturn(List.of(new MemberPlayer(1L, 10L), new MemberPlayer(2L, 20L)));

        service.discardCashOfMember(7L);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Account.class, first.getId()).getDeposit()).isZero();
        assertThat(entityManager.find(Account.class, second.getId()).getDeposit()).isZero();
        Account reloadedOther = entityManager.find(Account.class, other.getId());
        assertThat(reloadedOther.getDeposit()).isEqualTo(700_000);
        assertThat(reloadedOther.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("자산이 배분되지 않은 대기실 플레이어는 폐기할 현금이 없어도 정상 처리된다")
    void skipPlayerWithoutAccount() {
        BDDMockito.given(leagueQueryService.findStartedPlayersOfMember(7L))
                .willReturn(List.of(new MemberPlayer(3L, 30L)));

        service.discardCashOfMember(7L);

        assertThat(entityManager.getEntityManager()
                .createQuery("select count(a) from Account a", Long.class)
                .getSingleResult()).isZero();
    }
}
