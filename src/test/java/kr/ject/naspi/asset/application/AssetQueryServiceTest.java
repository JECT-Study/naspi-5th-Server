package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import kr.ject.naspi.asset.application.PlayerAsset.HoldingAsset;
import kr.ject.naspi.asset.domain.StockListing;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({AssetQueryService.class, StockQueryService.class,
        InitialAssetAllocationService.class})
class AssetQueryServiceTest {

    private static final long LEAGUE_ID = 1L;

    @Autowired
    private AssetQueryService service;

    @Autowired
    private InitialAssetAllocationService allocationService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("참여자는 현금, 종목별 보유 수량·평가 손익·수익률, 현재가 기준 총자산과 총자산 평가 손익을 조회할 수 있다")
    void getPlayerAsset() {
        // 참여자 3명: 상장자 2,500주, 다른 참여자 1,250주씩 → 보유 5,000주, 현금 5,000만 원
        long stockA = listStock(1L, "가나다");
        long stockB = listStock(2L, "라마바");
        long stockC = listStock(3L, "사아자");
        allocationService.allocate(LEAGUE_ID, List.of(
                new StockListing(stockA, 1L), new StockListing(stockB, 2L),
                new StockListing(stockC, 3L)));
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
                new StockListing(stockA, 1L), new StockListing(stockB, 2L)));

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
                new StockListing(stockA, 1L), new StockListing(stockB, 2L)));

        assertThatThrownBy(() -> service.getPlayerAsset(LEAGUE_ID, 1L))
                .isInstanceOf(NaspiRuntimeException.class);
    }

    private long listStock(long listerPlayerId, String name) {
        Stock stock = Stock.list(LEAGUE_ID, listerPlayerId, StockName.of(name),
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
