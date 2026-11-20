package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.asset.application.PlayerAsset.HoldingAsset;
import kr.ject.naspi.asset.application.PlayerTotalAsset.HoldingTotal;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles("test")
@DataJpaTest
@Import({AssetQueryService.class, StockQueryService.class, LocalStockSnapshotCache.class,
        InitialAssetAllocationService.class, AssetReservationService.class})
class AssetQueryServiceTest {

    private static final long LEAGUE_ID = 1L;

    @Autowired
    private AssetQueryService service;

    @Autowired
    private InitialAssetAllocationService allocationService;

    @Autowired
    private AssetReservationService reservationService;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoSpyBean
    private StockQueryService stockQueryService;

    @Test
    @DisplayName("참여자는 현금, 종목별 보유 수량·평가 손익·수익률, 현재가 기준 총자산과 총자산 평가 손익을 조회할 수 있다")
    void getPlayerAsset() {
        // 참여자 3명: 상장자 2,500주, 다른 참여자 1,250주씩 → 보유 5,000주, 현금 5,000만 원
        long stockA = listStock(1L, "가나다");
        long stockB = listStock(2L, "라마바");
        long stockC = listStock(3L, "사아자");
        allocationService.allocate(LEAGUE_ID, List.of(
                new ListedStock(stockA, 1L), new ListedStock(stockB, 2L),
                new ListedStock(stockC, 3L)));
        setCurrentPrice(stockA, 12_000);
        setCurrentPrice(stockB, 10_000);
        setCurrentPrice(stockC, 8_000);

        PlayerAsset asset = service.getPlayerAsset(LEAGUE_ID, 1L);

        assertThat(asset.deposit()).isEqualTo(50_000_000);
        assertThat(asset.reservedDeposit()).isZero();
        assertThat(asset.orderableDeposit()).isEqualTo(50_000_000);
        assertThat(asset.holdings())
                .extracting(HoldingAsset::stockId, HoldingAsset::stockName,
                        HoldingAsset::treasuryStock, HoldingAsset::quantity,
                        HoldingAsset::orderableQuantity, HoldingAsset::currentPrice,
                        HoldingAsset::valuation, HoldingAsset::profit, HoldingAsset::returnRate)
                .containsExactly(
                        tuple(stockA, "가나다", true, 2_500L, 1_500L, 12_000L, 30_000_000L,
                                5_000_000L, new BigDecimal("20.0")),
                        tuple(stockB, "라마바", false, 1_250L, 1_250L, 10_000L, 12_500_000L,
                                0L, new BigDecimal("0.0")),
                        tuple(stockC, "사아자", false, 1_250L, 1_250L, 8_000L, 10_000_000L,
                                -2_500_000L, new BigDecimal("-20.0")));
        assertThat(asset.stockValuation()).isEqualTo(52_500_000);
        assertThat(asset.totalAsset()).isEqualTo(102_500_000);
        assertThat(asset.totalProfit()).isEqualTo(2_500_000);
        assertThat(asset.totalReturnRate()).isEqualTo(new BigDecimal("2.5"));
    }

    @Test
    @DisplayName("초기 자산이 배분되지 않은 참여자는 자산을 조회할 수 없다")
    void assetNotFound() {
        assertThatThrownBy(() -> service.getPlayerAsset(LEAGUE_ID, 1L))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ASSET_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 리그의 자산은 조회되지 않는다")
    void otherLeague() {
        long stockA = listStock(1L, "가나다");
        long stockB = listStock(2L, "라마바");
        allocationService.allocate(LEAGUE_ID, List.of(
                new ListedStock(stockA, 1L), new ListedStock(stockB, 2L)));

        assertThatThrownBy(() -> service.getPlayerAsset(2L, 1L))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ASSET_NOT_FOUND);
    }

    @Test
    @DisplayName("보유 종목의 현재가가 정해지지 않았으면 평가할 수 없다")
    void currentPriceNotSet() {
        long stockA = listStock(1L, "가나다");
        long stockB = listStock(2L, "라마바");
        allocationService.allocate(LEAGUE_ID, List.of(
                new ListedStock(stockA, 1L), new ListedStock(stockB, 2L)));

        assertThatThrownBy(() -> service.getPlayerAsset(LEAGUE_ID, 1L))
                .isInstanceOf(NaspiRuntimeException.class);
    }

    @Test
    @DisplayName("같은 리그 참여자 여러 명의 자산을 한 번에 조회하면 한 명씩 조회한 결과와 같다")
    void getPlayerAssets() {
        startLeagueWithThreePlayers();

        List<PlayerAsset> assets = service.getPlayerAssets(LEAGUE_ID, List.of(3L, 1L, 2L));

        assertThat(assets).extracting(PlayerAsset::playerId).containsExactly(1L, 2L, 3L);
        assertThat(assets).containsExactly(
                service.getPlayerAsset(LEAGUE_ID, 1L),
                service.getPlayerAsset(LEAGUE_ID, 2L),
                service.getPlayerAsset(LEAGUE_ID, 3L));
    }

    @Test
    @DisplayName("여러 명을 조회해도 리그 종목·현재가는 한 번만 조회한다")
    void getPlayerAssetsQueriesStocksOnce() {
        startLeagueWithThreePlayers();
        clearInvocations(stockQueryService);

        service.getPlayerAssets(LEAGUE_ID, List.of(1L, 2L, 3L));

        verify(stockQueryService, times(1)).findAllByLeagueId(anyLong());
    }

    @Test
    @DisplayName("여러 명을 조회할 때 자산이 없거나 다른 리그인 플레이어는 오류 없이 빠진다")
    void getPlayerAssetsSkipsPlayersWithoutAsset() {
        startLeagueWithThreePlayers();

        List<PlayerAsset> assets = service.getPlayerAssets(LEAGUE_ID, List.of(1L, 1L, 99L));

        assertThat(assets).extracting(PlayerAsset::playerId).containsExactly(1L);
        assertThat(service.getPlayerAssets(2L, List.of(1L, 2L))).isEmpty();
        assertThat(service.getPlayerAssets(LEAGUE_ID, List.of())).isEmpty();
    }

    @Test
    @DisplayName("총자산은 현금과 자사주를 포함한 각 종목 보유 수량 × 호출자가 지정한 평가 가격의 합이다")
    void aggregateTotalAssets() {
        long[] stocks = startLeagueWithThreePlayers();
        Map<Long, Long> prices = Map.of(stocks[0], 9_000L, stocks[1], 11_000L, stocks[2], 10_000L);

        List<PlayerTotalAsset> assets = service.aggregateTotalAssets(LEAGUE_ID, prices);

        assertThat(assets).extracting(PlayerTotalAsset::playerId).containsExactly(1L, 2L, 3L);
        // 플레이어 1: 자사주 A 2,500주 + B 1,250주 + C 1,250주, 현금 5,000만 원
        PlayerTotalAsset first = assets.get(0);
        assertThat(first.deposit()).isEqualTo(50_000_000L);
        assertThat(first.stockValuation())
                .isEqualTo(2_500L * 9_000 + 1_250L * 11_000 + 1_250L * 10_000);
        assertThat(first.totalAsset()).isEqualTo(first.deposit() + first.stockValuation());
        assertThat(first.withdrawn()).isFalse();
        assertThat(first.holdings()).extracting(HoldingTotal::stockId, HoldingTotal::treasuryStock)
                .containsExactly(tuple(stocks[0], true), tuple(stocks[1], false),
                        tuple(stocks[2], false));
        assertThat(assets.get(1).holdings()).filteredOn(HoldingTotal::treasuryStock)
                .extracting(HoldingTotal::stockId).containsExactly(stocks[1]);
        assets.forEach(asset -> assertThat(asset.totalAsset())
                .isEqualTo(asset.deposit() + asset.holdings().stream()
                        .mapToLong(h -> h.quantity() * prices.get(h.stockId())).sum()));
    }

    @Test
    @DisplayName("평가 가격은 현재가가 아니라 호출자가 넘긴 가격을 쓴다")
    void aggregateUsesCallerPrices() {
        long[] stocks = startLeagueWithThreePlayers();

        PlayerTotalAsset first = service.aggregateTotalAssets(LEAGUE_ID,
                Map.of(stocks[0], 9_000L, stocks[1], 11_000L, stocks[2], 10_000L)).get(0);

        assertThat(first.holdings()).extracting(HoldingTotal::evaluationPrice)
                .containsExactly(9_000L, 11_000L, 10_000L);
        assertThat(first.holdings()).extracting(HoldingTotal::valuation)
                .containsExactly(22_500_000L, 13_750_000L, 12_500_000L);
        assertThat(first.totalAsset()).isEqualTo(50_000_000L + 48_750_000L);
    }

    @Test
    @DisplayName("예약된 현금과 주식도 총자산에 포함하되 중복 합산하지 않는다")
    void aggregateIncludesReservedAssets() {
        long[] stocks = startLeagueWithThreePlayers();
        Map<Long, Long> prices = Map.of(stocks[0], 9_000L, stocks[1], 11_000L, stocks[2], 10_000L);
        PlayerTotalAsset before = service.aggregateTotalAssets(LEAGUE_ID, prices).get(0);

        reservationService.reserveCashForBuy(LEAGUE_ID, 1L, 10_000, 100);
        reservationService.reserveStockForSell(LEAGUE_ID, 1L, stocks[1], 100);
        PlayerTotalAsset after = service.aggregateTotalAssets(LEAGUE_ID, prices).get(0);

        assertThat(after.deposit()).isEqualTo(before.deposit());
        assertThat(after.totalAsset()).isEqualTo(before.totalAsset());
        assertThat(after.holdings()).isEqualTo(before.holdings());
    }

    @Test
    @DisplayName("참여자별 현금, 종목별 보유 수량·평가액, 총자산을 함께 반환한다")
    void aggregateReturnsHoldingDetails() {
        long stockA = listStock(1L, "가나다");
        long stockB = listStock(2L, "라마바");
        allocationService.allocate(LEAGUE_ID, List.of(
                new ListedStock(stockA, 1L), new ListedStock(stockB, 2L)));
        // 종목 A 보유 수량을 모두 0으로 만든다. 수량 0 종목은 결과에서 빠지고 가격도 필요 없다
        entityManager.flush();
        entityManager.getEntityManager()
                .createNativeQuery("update holding set quantity = 0 where stock_id = :id")
                .setParameter("id", stockA)
                .executeUpdate();
        entityManager.clear();

        List<PlayerTotalAsset> assets = service.aggregateTotalAssets(LEAGUE_ID,
                Map.of(stockB, 10_000L));

        PlayerTotalAsset second = assets.get(1);
        assertThat(second.holdings()).extracting(HoldingTotal::stockId).containsExactly(stockB);
        assertThat(second.holdings()).allSatisfy(holding ->
                assertThat(holding.valuation())
                        .isEqualTo(holding.quantity() * holding.evaluationPrice()));
        assertThat(second.totalAsset())
                .isEqualTo(second.deposit() + second.stockValuation());
    }

    @Test
    @DisplayName("보유 종목의 평가 가격이 없으면 집계할 수 없다")
    void aggregateMissingPrice() {
        long[] stocks = startLeagueWithThreePlayers();

        assertThatThrownBy(() -> service.aggregateTotalAssets(LEAGUE_ID,
                Map.of(stocks[0], 9_000L, stocks[1], 11_000L)))
                .isInstanceOf(NaspiRuntimeException.class);
    }

    @Test
    @DisplayName("초기 자산이 배분되지 않은 리그는 빈 결과를 반환한다")
    void aggregateBeforeAllocation() {
        startLeagueWithThreePlayers();

        assertThat(service.aggregateTotalAssets(2L, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("다른 리그의 자산은 집계에 포함되지 않는다")
    void aggregateOtherLeagueIsolated() {
        long[] stocks = startLeagueWithThreePlayers();
        long otherA = persistStock(2L, 11L, "다른가");
        long otherB = persistStock(2L, 12L, "다른나");
        allocationService.allocate(2L, List.of(
                new ListedStock(otherA, 11L), new ListedStock(otherB, 12L)));

        List<PlayerTotalAsset> assets = service.aggregateTotalAssets(LEAGUE_ID,
                Map.of(stocks[0], 9_000L, stocks[1], 11_000L, stocks[2], 10_000L));

        assertThat(assets).extracting(PlayerTotalAsset::playerId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("탈퇴한 참여자는 현금 0, 보유 수량 그대로로 집계되고 탈퇴 여부가 표시된다")
    void aggregateWithdrawnPlayer() {
        final long[] stocks = startLeagueWithThreePlayers();
        Account account = entityManager.getEntityManager()
                .createQuery("select a from Account a where a.leagueId = :l and a.playerId = 1",
                        Account.class)
                .setParameter("l", LEAGUE_ID)
                .getSingleResult();
        account.discardForWithdrawal();
        entityManager.flush();
        entityManager.clear();

        PlayerTotalAsset first = service.aggregateTotalAssets(LEAGUE_ID,
                Map.of(stocks[0], 9_000L, stocks[1], 11_000L, stocks[2], 10_000L)).get(0);

        assertThat(first.deposit()).isZero();
        assertThat(first.withdrawn()).isTrue();
        assertThat(first.holdings()).extracting(HoldingTotal::quantity)
                .containsExactly(2_500L, 1_250L, 1_250L);
        assertThat(first.totalAsset()).isEqualTo(first.stockValuation());
    }

    /** 참여자 3명(플레이어 1·2·3)으로 리그를 시작하고 현재가를 정한다. */
    private long[] startLeagueWithThreePlayers() {
        long stockA = listStock(1L, "가나다");
        long stockB = listStock(2L, "라마바");
        long stockC = listStock(3L, "사아자");
        allocationService.allocate(LEAGUE_ID, List.of(
                new ListedStock(stockA, 1L), new ListedStock(stockB, 2L),
                new ListedStock(stockC, 3L)));
        setCurrentPrice(stockA, 12_000);
        setCurrentPrice(stockB, 10_000);
        setCurrentPrice(stockC, 8_000);
        return new long[] {stockA, stockB, stockC};
    }

    private long listStock(long listerPlayerId, String name) {
        return persistStock(LEAGUE_ID, listerPlayerId, name);
    }

    private long persistStock(long leagueId, long listerPlayerId, String name) {
        Stock stock = Stock.list(leagueId, listerPlayerId, StockName.of(name),
                StockDescription.of("소개"));
        return entityManager.persistAndGetId(stock, Long.class);
    }

    /** 현재가 갱신(MKT-04, MKT-05) 구현 전이라 DB 값을 직접 바꾼다. */
    private void setCurrentPrice(long stockId, long price) {
        entityManager.flush();
        entityManager.getEntityManager()
                .createNativeQuery("update stock set current_price = :price where id = :id")
                .setParameter("price", price)
                .setParameter("id", stockId)
                .executeUpdate();
        entityManager.clear();
    }
}
