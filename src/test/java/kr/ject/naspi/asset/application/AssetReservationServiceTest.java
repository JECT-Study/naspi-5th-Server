package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
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

/**
 * 예약·해제 규칙 자체는 {@code AccountTest}, {@code HoldingTest}가 검증한다. 여기서는 조회·자사주 판단과 저장만
 * 확인한다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import({AssetReservationService.class, StockQueryService.class, LocalStockSnapshotCache.class})
class AssetReservationServiceTest {

    private static final long LEAGUE_ID = 1L;
    private static final long PLAYER_ID = 10L;
    private static final long OTHER_PLAYER_ID = 11L;

    @Autowired
    private AssetReservationService service;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("매수 주문은 예약 기준 가격 × 주문 수량만큼 참여자의 현금을 예약한다")
    void reserveCashForBuy() {
        final long accountId = entityManager.persistAndGetId(
                Account.open(LEAGUE_ID, PLAYER_ID, 1_000_000), Long.class);

        long reserved = service.reserveCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 80);

        assertThat(reserved).isEqualTo(800_000);
        assertThat(reload(Account.class, accountId).getReservedDeposit()).isEqualTo(800_000);
    }

    @Test
    @DisplayName("초기 자산이 배분되지 않은 참여자는 매수 주문을 넣을 수 없다")
    void rejectBuyWithoutAccount() {
        assertThatThrownBy(() -> service.reserveCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ASSET_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 참여자가 상장한 종목은 보유 수량에서 예약 수량을 뺀 수량까지 매도 주문할 수 있다")
    void reserveStockForSell() {
        long stockId = listStock(LEAGUE_ID, OTHER_PLAYER_ID);
        final long holdingId = holding(stockId, 1_250);

        service.reserveStockForSell(LEAGUE_ID, PLAYER_ID, stockId, 1_250);

        assertThat(reload(Holding.class, holdingId).getReservedQuantity()).isEqualTo(1_250);
    }

    @Test
    @DisplayName("본인이 상장한 종목(자사주)은 최소 유지분 1,000주를 남기지 못하는 매도 주문을 거절한다")
    void rejectTreasuryStockSellBelowMinShares() {
        long stockId = listStock(LEAGUE_ID, PLAYER_ID);
        final long holdingId = holding(stockId, 2_500);
        service.reserveStockForSell(LEAGUE_ID, PLAYER_ID, stockId, 1_500);

        assertThatThrownBy(() -> service.reserveStockForSell(LEAGUE_ID, PLAYER_ID, stockId, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TREASURY_STOCK_MIN_SHARES);
        assertThat(reload(Holding.class, holdingId).getReservedQuantity()).isEqualTo(1_500);
    }

    @Test
    @DisplayName("보유하지 않은 종목은 매도 주문을 넣을 수 없다")
    void rejectSellWithoutHolding() {
        long stockId = listStock(LEAGUE_ID, OTHER_PLAYER_ID);

        assertThatThrownBy(() -> service.reserveStockForSell(LEAGUE_ID, PLAYER_ID, stockId, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);
    }

    @Test
    @DisplayName("리그에 없는 종목은 매도 주문을 넣을 수 없다")
    void rejectSellOfStockInOtherLeague() {
        long stockId = listStock(2L, OTHER_PLAYER_ID);

        assertThatThrownBy(() -> service.reserveStockForSell(LEAGUE_ID, PLAYER_ID, stockId, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("매수 주문이 취소·만료되면 미체결 잔량에 묶인 현금을 해제한다")
    void releaseCashForBuy() {
        final long accountId = entityManager.persistAndGetId(
                Account.open(LEAGUE_ID, PLAYER_ID, 1_000_000), Long.class);
        service.reserveCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 80);

        long released = service.releaseCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 30);

        assertThat(released).isEqualTo(300_000);
        assertThat(reload(Account.class, accountId).getReservedDeposit()).isEqualTo(500_000);
    }

    @Test
    @DisplayName("매도 주문이 취소·만료되면 미체결 잔량만큼 예약 주식을 해제한다")
    void releaseStockForSell() {
        long stockId = listStock(LEAGUE_ID, OTHER_PLAYER_ID);
        final long holdingId = holding(stockId, 1_250);
        service.reserveStockForSell(LEAGUE_ID, PLAYER_ID, stockId, 400);

        service.releaseStockForSell(LEAGUE_ID, PLAYER_ID, stockId, 400);

        assertThat(reload(Holding.class, holdingId).getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("해제할 계좌나 보유 주식이 없으면 데이터 오류로 본다")
    void rejectReleaseWithoutAsset() {
        assertThatThrownBy(() -> service.releaseCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 1))
                .isInstanceOf(NaspiRuntimeException.class);
        assertThatThrownBy(() -> service.releaseStockForSell(LEAGUE_ID, PLAYER_ID, 100L, 1))
                .isInstanceOf(NaspiRuntimeException.class);
    }

    private long listStock(long leagueId, long listerPlayerId) {
        Stock stock = Stock.list(leagueId, listerPlayerId, StockName.of("종목" + listerPlayerId),
                StockDescription.of("소개"));
        return entityManager.persistAndGetId(stock, Long.class);
    }

    private long holding(long stockId, long quantity) {
        return entityManager.persistAndGetId(
                Holding.allocate(LEAGUE_ID, PLAYER_ID, stockId, quantity), Long.class);
    }

    private <T> T reload(Class<T> type, long id) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(type, id);
    }
}
