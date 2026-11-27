package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.league.domain.DailyAssetSnapshot;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래일별 자산 집계(LEA-18). asset 총자산 집계를 실제로 호출해 저장된 집계를 확인한다. 거래일 전환 트랜잭션 안에서
 * 호출되는 형태로 테스트 트랜잭션 안에서 호출한다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import({DailyAssetSnapshotUseCase.class, AssetQueryService.class, StockQueryService.class,
        LocalStockSnapshotCache.class})
class DailyAssetSnapshotUseCaseTest {

    private static final long LEAGUE_ID = 1L;
    private static final int DAY = 1;
    private static final long LISTER = 10L;
    private static final long HOLDER = 20L;

    @Autowired
    private DailyAssetSnapshotUseCase useCase;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("거래일별 총자산은 전체 현금과 보유 주식을 그 거래일 종가로 평가한 금액의 합이다")
    void evaluatesHoldingsAtClosePrice() {
        long stockId = stock(LISTER, "가나다");
        // 현재가는 종가와 다르게 둔다
        entityManager.find(Stock.class, stockId).applyTrade(15_000);
        account(HOLDER, 50_000_000);
        holding(HOLDER, stockId, 3_000);
        entityManager.flush();

        useCase.record(LEAGUE_ID, DAY, Map.of(stockId, 12_300L));

        assertThat(snapshots()).extracting(DailyAssetSnapshot::getPlayerId,
                        DailyAssetSnapshot::getCash, DailyAssetSnapshot::getStockValuation,
                        DailyAssetSnapshot::getTotalAsset)
                .containsExactly(tuple(HOLDER, 50_000_000L, 36_900_000L, 86_900_000L));
    }

    @Test
    @DisplayName("거래일별 주식 평가금액과 총자산에는 자사주를 포함한다")
    void includesTreasuryStock() {
        long own = stock(LISTER, "가나다");
        long other = stock(HOLDER, "라마바");
        account(LISTER, 10_000_000);
        holding(LISTER, own, 3_000);
        holding(LISTER, other, 100);
        entityManager.flush();

        useCase.record(LEAGUE_ID, DAY, Map.of(own, 11_000L, other, 9_000L));

        DailyAssetSnapshot snapshot = snapshots().getFirst();
        assertThat(snapshot.getStockValuation()).isEqualTo(3_000L * 11_000 + 100L * 9_000);
        assertThat(snapshot.getTotalAsset())
                .isEqualTo(10_000_000L + 3_000L * 11_000 + 100L * 9_000);
    }

    @Test
    @DisplayName("거래일 종료 시점의 예약된 현금과 주식도 현금·평가금액에 포함하고 중복 합산하지 않는다")
    void includesReservedAssets() {
        long stockId = stock(LISTER, "가나다");
        Account account = account(HOLDER, 50_000_000);
        Holding holding = holding(HOLDER, stockId, 1_000);
        account.reserveForBuy(10_000, 100);
        holding.reserveForSell(300, false);
        entityManager.flush();

        useCase.record(LEAGUE_ID, DAY, Map.of(stockId, 10_000L));

        assertThat(snapshots()).extracting(DailyAssetSnapshot::getCash,
                        DailyAssetSnapshot::getStockValuation, DailyAssetSnapshot::getTotalAsset)
                .containsExactly(tuple(50_000_000L, 10_000_000L, 60_000_000L));
    }

    @Test
    @DisplayName("탈퇴한 참여자도 거래일별 자산을 기록한다")
    void includesWithdrawnPlayer() {
        long stockId = stock(LISTER, "가나다");
        Account withdrawn = account(LISTER, 40_000_000);
        holding(LISTER, stockId, 2_000);
        withdrawn.discardForWithdrawal();
        account(HOLDER, 100_000_000);
        entityManager.flush();

        useCase.record(LEAGUE_ID, DAY, Map.of(stockId, 10_000L));

        assertThat(snapshots()).extracting(DailyAssetSnapshot::getPlayerId,
                        DailyAssetSnapshot::getCash, DailyAssetSnapshot::getStockValuation,
                        DailyAssetSnapshot::getTotalAsset)
                .containsExactly(
                        tuple(LISTER, 0L, 20_000_000L, 20_000_000L),
                        tuple(HOLDER, 100_000_000L, 0L, 100_000_000L));
    }

    @Test
    @DisplayName("같은 거래일의 자산 집계를 두 번 저장하지 않는다")
    void rejectsDuplicate() {
        account(HOLDER, 100_000_000);
        entityManager.flush();
        useCase.record(LEAGUE_ID, DAY, Map.of());

        assertThatThrownBy(() -> useCase.record(LEAGUE_ID, DAY, Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(snapshots()).hasSize(1);
    }

    @Test
    @DisplayName("자산이 배분되지 않은 리그는 거래일 자산 집계를 저장하지 않는다")
    void skipsWithoutAccounts() {
        useCase.record(LEAGUE_ID, DAY, Map.of());

        assertThat(snapshots()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("거래일 전환 트랜잭션 밖에서는 자산 집계를 저장할 수 없다")
    void requiresTransaction() {
        assertThatThrownBy(() -> useCase.record(LEAGUE_ID, DAY, Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> useCase.save(LEAGUE_ID, DAY, List.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private long stock(long listerPlayerId, String name) {
        Stock stock = Stock.list(LEAGUE_ID, listerPlayerId, StockName.of(name),
                StockDescription.of("소개"));
        stock.open();
        return entityManager.persistAndGetId(stock, Long.class);
    }

    private Account account(long playerId, long cash) {
        return entityManager.persist(Account.open(LEAGUE_ID, playerId, cash));
    }

    private Holding holding(long playerId, long stockId, long quantity) {
        return entityManager.persist(Holding.allocate(LEAGUE_ID, playerId, stockId, quantity));
    }

    private List<DailyAssetSnapshot> snapshots() {
        entityManager.flush();
        return entityManager.getEntityManager().createQuery(
                        "select d from DailyAssetSnapshot d where d.leagueId = :l "
                                + "and d.tradingDay = :t order by d.playerId",
                        DailyAssetSnapshot.class)
                .setParameter("l", LEAGUE_ID).setParameter("t", DAY).getResultList();
    }
}
