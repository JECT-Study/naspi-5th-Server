package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.application.AssetSettlementService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueTradingService;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 접수 순서(락 → 상·하한가 → 예약 → 매칭 → 정산)를 실제 asset·league·market 서비스와 함께 확인한다.
 * 매칭·예약·정산 규칙 자체는 각 도메인 테스트가 검증한다.
 */
@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({LimitOrderService.class, LeagueTradingService.class, StockQueryService.class,
        LocalStockSnapshotCache.class, AssetReservationService.class,
        AssetSettlementService.class, LimitOrderServiceTest.FixedClockConfig.class})
class LimitOrderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final long LISTER = 1L;
    private static final long SELLER = 20L;
    private static final long BUYER = 30L;
    private static final long BASE_PRICE = 10_000;
    private static final int TRADING_DAY = 2;

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(ClockConfig.KST).toInstant(), ClockConfig.KST);
        }
    }

    @Autowired
    private LimitOrderService service;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    private long leagueId;
    private long stockId;

    @BeforeEach
    void setUp() {
        leagueId = startedLeague().getId();
        stockId = entityManager.persistAndGetId(
                Stock.list(leagueId, LISTER, StockName.of("종목"), StockDescription.of("소개")),
                Long.class);
        for (long player : new long[] {LISTER, SELLER, BUYER}) {
            entityManager.persist(Account.open(leagueId, player, 1_000_000));
            entityManager.persist(Holding.allocate(leagueId, player, stockId,
                    player == LISTER ? 3_000 : player == SELLER ? 100 : 0));
        }
        entityManager.flush();
    }

    @Test
    @DisplayName("매수 지정가 주문은 가격 × 수량만큼 현금을 예약하고 미체결 잔량은 대기 주문으로 남는다")
    void buyOrderReservesCashAndRests() {
        PlacedOrder placed = place(BUYER, OrderSide.BUY, 9_000, 10);

        assertThat(placed.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(placed.remainingQuantity()).isEqualTo(10);
        Order saved = entityManager.find(Order.class, placed.orderId());
        assertThat(saved.getReservedPrice()).isEqualTo(9_000);
        assertThat(saved.getTradingDay()).isEqualTo(TRADING_DAY);
        assertThat(saved.getOrderedAt()).isEqualTo(NOW);
        assertThat(account(BUYER).getReservedDeposit()).isEqualTo(90_000);
        assertThat(events.stream(TradeExecuted.class)).isEmpty();
    }

    @Test
    @DisplayName("매도 지정가 주문은 주문 수량만큼 주식을 예약한다")
    void sellOrderReservesStock() {
        place(SELLER, OrderSide.SELL, 11_000, 30);

        assertThat(holding(SELLER).getReservedQuantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("당일 상·하한가 범위 밖 가격은 접수를 거절하고 상·하한가 가격은 접수한다")
    void priceLimit() {
        assertThatThrownBy(() -> place(BUYER, OrderSide.BUY, 13_100, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_PRICE_OUT_OF_LIMIT);
        assertThatThrownBy(() -> place(BUYER, OrderSide.BUY, 6_900, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_PRICE_OUT_OF_LIMIT);

        assertThat(place(BUYER, OrderSide.BUY, 13_000, 1).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(place(BUYER, OrderSide.BUY, 7_000, 1).status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 가능 현금이 부족하면 접수를 거절한다")
    void rejectInsufficientDeposit() {
        assertThatThrownBy(() -> place(BUYER, OrderSide.BUY, 10_000, 101))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_DEPOSIT);
    }

    @Test
    @DisplayName("매도 가능 수량이 부족하면 접수를 거절한다")
    void rejectInsufficientQuantity() {
        assertThatThrownBy(() -> place(SELLER, OrderSide.SELL, 10_000, 101))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);
    }

    @Test
    @DisplayName("거래 가능 상태가 아닌 리그에서는 접수를 거절한다")
    void rejectNotTradableLeague() {
        League waiting = entityManager.persistAndFlush(League.create(
                LeagueName.of("대기실"), InviteCode.of("BBBBBBB"), NOW.minusDays(1)));

        assertThatThrownBy(() -> service.placeLimitOrder(new PlaceLimitOrder(
                waiting.getId(), BUYER, stockId, OrderSide.BUY, 9_000, 1), TRADING_DAY, BASE_PRICE))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE);
    }

    @Test
    @DisplayName("리그에 없는 종목은 접수를 거절한다")
    void rejectUnknownStock() {
        assertThatThrownBy(() -> service.placeLimitOrder(new PlaceLimitOrder(
                leagueId, BUYER, 9_999L, OrderSide.BUY, 9_000, 1), TRADING_DAY, BASE_PRICE))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("접수 직후 매칭되어 체결가는 먼저 대기하던 주문의 가격이고 체결은 정산되어 이벤트로 발행된다")
    void matchImmediatelyAtRestingPrice() {
        PlacedOrder sell = place(SELLER, OrderSide.SELL, 11_000, 10);

        PlacedOrder buy = place(BUYER, OrderSide.BUY, 12_000, 4);

        assertThat(buy.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(entityManager.find(Order.class, sell.orderId()).getStatus())
                .isEqualTo(OrderStatus.PARTIALLY_FILLED);
        List<TradeExecuted> trades = events.stream(TradeExecuted.class).toList();
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).price()).isEqualTo(11_000);
        assertThat(trades.get(0).quantity()).isEqualTo(4);
        assertThat(trades.get(0).buyOrderId()).isEqualTo(buy.orderId());
        assertThat(trades.get(0).sellOrderId()).isEqualTo(sell.orderId());

        // 매수자는 체결가로 정산되고, 예약 기준 가격(12,000)과의 차액은 해제된다.
        assertThat(account(BUYER).getDeposit()).isEqualTo(1_000_000 - 44_000);
        assertThat(account(BUYER).getReservedDeposit()).isZero();
        assertThat(holding(BUYER).getQuantity()).isEqualTo(4);
        assertThat(account(SELLER).getDeposit()).isEqualTo(1_000_000 + 44_000);
        assertThat(holding(SELLER).getQuantity()).isEqualTo(96);
        assertThat(holding(SELLER).getReservedQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("상장자가 탈퇴해도 그 종목은 다른 참여자끼리 계속 매수·매도해 체결할 수 있다")
    void tradeStockOfWithdrawnLister() {
        Account lister = account(LISTER);
        lister.discardForWithdrawal();
        entityManager.flush();
        entityManager.clear();

        PlacedOrder sell = place(SELLER, OrderSide.SELL, 10_500, 5);
        PlacedOrder buy = place(BUYER, OrderSide.BUY, 10_500, 5);

        assertThat(sell.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(buy.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(entityManager.find(Order.class, sell.orderId()).getStatus())
                .isEqualTo(OrderStatus.FILLED);
        assertThat(holding(BUYER).getQuantity()).isEqualTo(5);
        assertThat(holding(SELLER).getQuantity()).isEqualTo(95);
        // 탈퇴한 상장자의 보유분은 그대로 남는다.
        assertThat(holding(LISTER).getQuantity()).isEqualTo(3_000);
    }

    @Test
    @DisplayName("일부만 체결되면 부분 체결 상태로 미체결 잔량이 대기 주문으로 남는다")
    void partiallyFilledRests() {
        place(SELLER, OrderSide.SELL, 11_000, 3);

        PlacedOrder buy = place(BUYER, OrderSide.BUY, 11_000, 10);

        assertThat(buy.status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(buy.remainingQuantity()).isEqualTo(7);
        assertThat(account(BUYER).getReservedDeposit()).isEqualTo(77_000);
    }

    @Test
    @DisplayName("자전거래가 되는 신규 주문은 미체결 잔량이 취소되고 예약이 해제되며 기존 대기 주문은 유지된다")
    void selfTradePreventedReleasesReservation() {
        PlacedOrder sell = place(SELLER, OrderSide.SELL, 11_000, 10);

        PlacedOrder buy = place(SELLER, OrderSide.BUY, 11_000, 5);

        assertThat(buy.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(account(SELLER).getReservedDeposit()).isZero();
        assertThat(entityManager.find(Order.class, sell.orderId()).getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(events.stream(TradeExecuted.class)).isEmpty();
    }

    @Test
    @DisplayName("매수 주문을 취소하면 미체결 잔여 수량에 묶인 현금 예약이 해제된다")
    void cancelBuyReleasesCash() {
        PlacedOrder buy = place(BUYER, OrderSide.BUY, 9_000, 10);

        CanceledOrder canceled = cancel(BUYER, buy.orderId());

        assertThat(canceled.canceledQuantity()).isEqualTo(10);
        assertThat(canceled.filledQuantity()).isZero();
        assertThat(entityManager.find(Order.class, buy.orderId()).getStatus())
                .isEqualTo(OrderStatus.CANCELED);
        assertThat(account(BUYER).getReservedDeposit()).isZero();
        assertThat(account(BUYER).getDeposit()).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("부분 체결된 주문을 취소하면 이미 체결된 부분은 유지되고 미체결 잔여 수량의 예약만 해제된다")
    void cancelPartiallyFilledKeepsFilledPart() {
        PlacedOrder sell = place(SELLER, OrderSide.SELL, 11_000, 10);
        place(BUYER, OrderSide.BUY, 11_000, 3);

        CanceledOrder canceled = cancel(SELLER, sell.orderId());

        assertThat(canceled.filledQuantity()).isEqualTo(3);
        assertThat(canceled.canceledQuantity()).isEqualTo(7);
        assertThat(holding(SELLER).getQuantity()).isEqualTo(97);
        assertThat(holding(SELLER).getReservedQuantity()).isZero();
        assertThat(account(SELLER).getDeposit()).isEqualTo(1_000_000 + 33_000);
        assertThat(holding(BUYER).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("취소된 주문은 호가창에서 빠져 이후 주문과 체결되지 않는다")
    void canceledOrderNoLongerMatches() {
        PlacedOrder sell = place(SELLER, OrderSide.SELL, 11_000, 10);
        cancel(SELLER, sell.orderId());

        PlacedOrder buy = place(BUYER, OrderSide.BUY, 11_000, 10);

        assertThat(buy.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(events.stream(TradeExecuted.class)).isEmpty();
    }

    @Test
    @DisplayName("주문을 취소하고 같은 가격으로 다시 넣으면 새 접수 시각 기준이므로 먼저 대기하던 다른 주문보다 뒤에 체결된다")
    void reorderedOrderLosesTimePriority() {
        PlacedOrder original = place(SELLER, OrderSide.SELL, 11_000, 10);
        PlacedOrder other = place(LISTER, OrderSide.SELL, 11_000, 10);
        cancel(SELLER, original.orderId());
        PlacedOrder reordered = place(SELLER, OrderSide.SELL, 11_000, 10);

        place(BUYER, OrderSide.BUY, 11_000, 10);

        // 취소 전이었다면 original이 other보다 먼저 접수되어 먼저 체결된다. 재주문은 새 주문이라 가장 뒤에 선다.
        assertThat(reordered.orderId()).isGreaterThan(other.orderId());
        assertThat(entityManager.find(Order.class, other.orderId()).getStatus())
                .isEqualTo(OrderStatus.FILLED);
        assertThat(entityManager.find(Order.class, reordered.orderId()).getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(entityManager.find(Order.class, original.orderId()).getStatus())
                .isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("본인 주문이 아니면 취소할 수 없고 주문은 그대로 남는다")
    void rejectCancelOfOthersOrder() {
        PlacedOrder buy = place(BUYER, OrderSide.BUY, 9_000, 10);

        assertThatThrownBy(() -> cancel(SELLER, buy.orderId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThat(entityManager.find(Order.class, buy.orderId()).getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(account(BUYER).getReservedDeposit()).isEqualTo(90_000);
    }

    @Test
    @DisplayName("요청한 리그의 주문이 아니거나 없는 주문은 취소할 수 없다")
    void rejectCancelOfUnknownOrder() {
        PlacedOrder buy = place(BUYER, OrderSide.BUY, 9_000, 10);
        League other = startedLeague("CCCCCCC");

        assertThatThrownBy(() -> service.cancelOrder(other.getId(), BUYER, buy.orderId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThatThrownBy(() -> cancel(BUYER, 9_999L))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("체결 완료되었거나 이미 취소된 주문은 취소할 수 없고 예약이 두 번 해제되지 않는다")
    void rejectCancelOfClosedOrder() {
        PlacedOrder sell = place(SELLER, OrderSide.SELL, 11_000, 5);
        PlacedOrder filled = place(BUYER, OrderSide.BUY, 11_000, 5);
        PlacedOrder buy = place(BUYER, OrderSide.BUY, 9_000, 10);
        cancel(BUYER, buy.orderId());

        assertThatThrownBy(() -> cancel(BUYER, filled.orderId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThatThrownBy(() -> cancel(SELLER, sell.orderId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThatThrownBy(() -> cancel(BUYER, buy.orderId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(account(BUYER).getReservedDeposit()).isZero();
    }

    @Test
    @DisplayName("거래 가능 상태가 아닌 리그에서는 취소를 거절한다")
    void rejectCancelInNotTradableLeague() {
        League waiting = entityManager.persistAndFlush(League.create(
                LeagueName.of("대기실"), InviteCode.of("BBBBBBB"), NOW.minusDays(1)));

        assertThatThrownBy(() -> service.cancelOrder(waiting.getId(), BUYER, 1L))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE);
    }

    private CanceledOrder cancel(long playerId, long orderId) {
        CanceledOrder canceled = service.cancelOrder(leagueId, playerId, orderId);
        entityManager.flush();
        entityManager.clear();
        return canceled;
    }

    private PlacedOrder place(long playerId, OrderSide side, long price, long quantity) {
        PlacedOrder placed = service.placeLimitOrder(new PlaceLimitOrder(
                leagueId, playerId, stockId, side, price, quantity), TRADING_DAY, BASE_PRICE);
        entityManager.flush();
        entityManager.clear();
        return placed;
    }

    private Account account(long playerId) {
        return entityManager.getEntityManager().createQuery(
                        "select a from Account a where a.leagueId = :l and a.playerId = :p",
                        Account.class)
                .setParameter("l", leagueId).setParameter("p", playerId).getSingleResult();
    }

    private Holding holding(long playerId) {
        return entityManager.getEntityManager().createQuery(
                        "select h from Holding h where h.leagueId = :l and h.playerId = :p",
                        Holding.class)
                .setParameter("l", leagueId).setParameter("p", playerId).getSingleResult();
    }

    private League startedLeague() {
        return startedLeague("AAAAAAA");
    }

    private League startedLeague(String inviteCode) {
        League league = entityManager.persistAndFlush(League.create(
                LeagueName.of("리그"), InviteCode.of(inviteCode), NOW.minusDays(1)));
        List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(league.getId(), memberId, NOW.minusDays(1));
                    player.completeListing(NOW.minusDays(1));
                    return player;
                })
                .toList();
        league.start(players, NOW);
        return entityManager.persistAndFlush(league);
    }
}
