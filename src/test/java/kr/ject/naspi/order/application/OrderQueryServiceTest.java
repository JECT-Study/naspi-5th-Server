package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.application.AssetSettlementService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
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
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderFill;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderType;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@DataJpaTest
@Import({OrderQueryService.class, LimitOrderService.class, MarketOrderService.class,
        OrderExecutionSupport.class, LeagueTradingService.class, LeagueQueryService.class,
        StockQueryService.class, LocalStockSnapshotCache.class, AssetReservationService.class,
        AssetSettlementService.class, OrderQueryServiceTest.ClockConfig.class})
class OrderQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final long BASE_PRICE = 10_000;
    private static final long LISTER_MEMBER = 1L;
    private static final long SELLER_MEMBER = 2L;
    private static final long BUYER_MEMBER = 3L;
    private static final long OUTSIDER_MEMBER = 99L;

    @TestConfiguration
    static class ClockConfig {

        @Bean
        Clock clock() {
            return new LimitOrderServiceTest.SettableClock(NOW);
        }
    }

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private OrderQueryService queryService;

    @Autowired
    private LimitOrderService limitOrderService;

    @Autowired
    private MarketOrderService marketOrderService;

    @Autowired
    private Clock clock;

    @Autowired
    private TestEntityManager entityManager;

    private League league;
    private long leagueId;
    private long stockId;
    private long listerPlayer;
    private long sellerPlayer;
    private long buyerPlayer;

    @BeforeEach
    void setUp() {
        setClock(NOW);
        league = startedLeague("AAAAAAA", NOW, true);
        leagueId = league.getId();
        List<Long> players = playerIds(leagueId);
        listerPlayer = players.get(0);
        sellerPlayer = players.get(1);
        buyerPlayer = players.get(2);
        stockId = entityManager.persistAndGetId(Stock.list(leagueId, listerPlayer,
                StockName.of("종목"), StockDescription.of("소개")), Long.class);
        for (long player : players) {
            entityManager.persist(Account.open(leagueId, player, 1_000_000));
            entityManager.persist(Holding.allocate(leagueId, player, stockId,
                    player == listerPlayer ? 3_000 : player == sellerPlayer ? 100 : 0));
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("지정가 주문 상세는 주문 가격·체결 내역·현재 대기 수량을 제공하고 취소 내역은 제공하지 않는다")
    void limitPartiallyFilled() {
        final long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        setClock(NOW.plusMinutes(1));
        limit(buyerPlayer, OrderSide.BUY, 11_000, 3);
        setClock(NOW.plusMinutes(2));
        limit(buyerPlayer, OrderSide.BUY, 11_000, 2);

        OrderDetail detail = queryService.getOrderDetail(SELLER_MEMBER, leagueId, sell);

        assertThat(detail.orderId()).isEqualTo(sell);
        assertThat(detail.stockName()).isEqualTo("종목");
        assertThat(detail.side()).isEqualTo(OrderSide.SELL);
        assertThat(detail.type()).isEqualTo(OrderType.LIMIT);
        assertThat(detail.orderedAt()).isEqualTo(NOW);
        assertThat(detail.quantity()).isEqualTo(10);
        assertThat(detail.price()).isEqualTo(11_000L);
        assertThat(detail.fills()).containsExactly(
                new OrderFill(NOW.plusMinutes(1), 11_000, 3, 33_000),
                new OrderFill(NOW.plusMinutes(2), 11_000, 2, 22_000));
        assertThat(detail.pendingQuantity()).isEqualTo(5L);
        assertThat(detail.cancellation()).isNull();
    }

    @Test
    @DisplayName("시장가 주문은 주문 가격을 제공하지 않고, 잔량 취소는 '체결 가능 수량 부족' 사유로 취소 수량에 포함한다")
    void marketPartiallyFilled() {
        limit(sellerPlayer, OrderSide.SELL, 10_000, 4);
        long market = market(buyerPlayer, OrderSide.BUY, 10);

        OrderDetail detail = queryService.getOrderDetail(BUYER_MEMBER, leagueId, market);

        assertThat(detail.type()).isEqualTo(OrderType.MARKET);
        assertThat(detail.price()).isNull();
        assertThat(detail.fills()).containsExactly(new OrderFill(NOW, 10_000, 4, 40_000));
        assertThat(detail.cancellation()).isEqualTo(new OrderDetail.Cancellation(6, NOW,
                CancelReason.MARKET_PARTIALLY_FILLED));
        assertThat(detail.pendingQuantity()).isNull();
    }

    @Test
    @DisplayName("시장가 체결 없이 전량 취소되면 취소 수량은 최초 수량이고 체결 내역은 비어 있다")
    void marketNotFilled() {
        long market = market(buyerPlayer, OrderSide.BUY, 10);

        OrderDetail detail = queryService.getOrderDetail(BUYER_MEMBER, leagueId, market);

        assertThat(detail.fills()).isEmpty();
        assertThat(detail.cancellation()).isEqualTo(new OrderDetail.Cancellation(10, NOW,
                CancelReason.MARKET_NOT_FILLED));
        assertThat(detail.pendingQuantity()).isNull();
    }

    @Test
    @DisplayName("부분 체결 후 직접 취소하면 취소 수량·취소 일시·직접 취소 사유를 제공하고 체결 내역은 유지된다")
    void userCanceledAfterPartialFill() {
        long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        limit(buyerPlayer, OrderSide.BUY, 11_000, 3);
        setClock(NOW.plusMinutes(30));
        limitOrderService.cancelOrder(leagueId, sellerPlayer, sell);
        entityManager.flush();
        entityManager.clear();

        OrderDetail detail = queryService.getOrderDetail(SELLER_MEMBER, leagueId, sell);

        assertThat(detail.fills()).containsExactly(new OrderFill(NOW, 11_000, 3, 33_000));
        assertThat(detail.cancellation()).isEqualTo(new OrderDetail.Cancellation(7,
                NOW.plusMinutes(30), CancelReason.USER_REQUESTED));
        assertThat(detail.pendingQuantity()).isNull();
    }

    @Test
    @DisplayName("거래일 변경으로 만료된 잔여 수량은 취소 수량에 합산하고 사유는 거래일 변경 만료다")
    void expired() {
        long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        limit(buyerPlayer, OrderSide.BUY, 11_000, 3);
        LocalDateTime switchAt = LocalDateTime.of(2026, 11, 16, 9, 0);
        entityManager.find(Order.class, sell).expire(switchAt);
        entityManager.flush();
        entityManager.clear();

        OrderDetail detail = queryService.getOrderDetail(SELLER_MEMBER, leagueId, sell);

        assertThat(detail.cancellation()).isEqualTo(new OrderDetail.Cancellation(7, switchAt,
                CancelReason.TRADING_DAY_EXPIRED));
        assertThat(detail.pendingQuantity()).isNull();
    }

    @Test
    @DisplayName("자전거래로 취소된 주문은 내 주문 간 체결 방지 사유를 제공한다")
    void selfTradePrevented() {
        limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        long buy = limit(sellerPlayer, OrderSide.BUY, 11_000, 5);

        OrderDetail detail = queryService.getOrderDetail(SELLER_MEMBER, leagueId, buy);

        assertThat(detail.cancellation().reason()).isEqualTo(CancelReason.SELF_TRADE_PREVENTED);
        assertThat(detail.cancellation().quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("타인의 주문은 조회할 수 없다")
    void otherPlayersOrder() {
        long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);

        assertThatThrownBy(() -> queryService.getOrderDetail(BUYER_MEMBER, leagueId, sell))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("다른 리그의 주문은 본인 주문이어도 조회할 수 없다")
    void orderOfOtherLeague() {
        long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        League other = startedLeague("CCCCCCC", NOW, false);

        assertThatThrownBy(() -> queryService.getOrderDetail(SELLER_MEMBER, other.getId(), sell))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("없는 주문은 주문 없음 코드로 거절한다")
    void unknownOrder() {
        assertThatThrownBy(() -> queryService.getOrderDetail(SELLER_MEMBER, leagueId, 9_999L))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 조회할 수 없다")
    void notJoined() {
        assertThatThrownBy(() -> queryService.getOrderDetail(OUTSIDER_MEMBER, leagueId, 1L))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("대기실의 주문 조회는 아직 시작하지 않은 리그 코드로 거절한다")
    void waitingRoom() {
        League waiting = entityManager.persistAndFlush(League.create(
                LeagueName.of("대기실"), InviteCode.of("BBBBBBB"), NOW.minusDays(1)));
        entityManager.persistAndFlush(Player.enter(waiting.getId(), SELLER_MEMBER, NOW));

        assertThatThrownBy(() -> queryService.getOrderDetail(SELLER_MEMBER, waiting.getId(), 1L))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE));
    }

    @Test
    @DisplayName("종료 시각 이후의 리그는 종료된 리그 코드로 거절한다")
    void endedLeague() {
        long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        setClock(league.getEndsAt());

        assertThatThrownBy(() -> queryService.getOrderDetail(SELLER_MEMBER, leagueId, sell))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
    }

    @Test
    @DisplayName("상대방 회원이 탈퇴해도 본인의 체결 내역은 그대로 조회된다")
    void counterpartyWithdrawn() {
        long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        limit(buyerPlayer, OrderSide.BUY, 11_000, 3);
        final OrderDetail before = queryService.getOrderDetail(SELLER_MEMBER, leagueId, sell);

        entityManager.find(Player.class, buyerPlayer).withdraw(NOW.plusMinutes(5));
        entityManager.flush();
        entityManager.clear();

        assertThat(queryService.getOrderDetail(SELLER_MEMBER, leagueId, sell).fills())
                .isEqualTo(before.fills()).hasSize(1);
    }

    @Test
    @DisplayName("대기 목록에는 미체결 잔여 수량이 있는 매수·매도 주문이 종목 이름·수량·매매 구분·주문 가격과 함께 포함된다")
    void pendingList() {
        final long filled = limit(sellerPlayer, OrderSide.SELL, 10_500, 1);
        limit(buyerPlayer, OrderSide.BUY, 10_500, 1);
        long pendingSell = limit(sellerPlayer, OrderSide.SELL, 12_000, 10);
        long partialSell = limit(sellerPlayer, OrderSide.SELL, 11_000, 4);
        limit(buyerPlayer, OrderSide.BUY, 11_000, 1);
        long pendingBuy = limit(sellerPlayer, OrderSide.BUY, 9_000, 3);

        List<OrderSummary> list = queryService.getOrders(SELLER_MEMBER, leagueId,
                OrderListType.PENDING, null);

        assertThat(list).extracting(OrderSummary::orderId)
                .containsExactly(pendingBuy, partialSell, pendingSell);
        OrderSummary buy = list.get(0);
        assertThat(buy.stockName()).isEqualTo("종목");
        assertThat(buy.side()).isEqualTo(OrderSide.BUY);
        assertThat(buy.price()).isEqualTo(9_000L);
        assertThat(buy.quantity()).isEqualTo(3);
        assertThat(buy.pendingQuantity()).isEqualTo(3L);
        assertThat(list).extracting(OrderSummary::orderId).doesNotContain(filled);
    }

    @Test
    @DisplayName("일부 체결 후 잔여 수량이 대기 중인 주문은 대기 목록과 완료 목록에 모두 포함된다")
    void partiallyFilledInBothLists() {
        long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        limit(buyerPlayer, OrderSide.BUY, 11_000, 3);

        assertThat(queryService.getOrders(SELLER_MEMBER, leagueId, OrderListType.PENDING, null))
                .extracting(OrderSummary::orderId).containsExactly(sell);
        List<OrderSummary> completed = queryService.getOrders(SELLER_MEMBER, leagueId,
                OrderListType.COMPLETED, null);
        assertThat(completed).extracting(OrderSummary::orderId).containsExactly(sell);
        assertThat(completed.get(0).filledQuantity()).isEqualTo(3L);
        assertThat(completed.get(0).pendingQuantity()).isEqualTo(7L);
        assertThat(completed.get(0).canceledQuantity()).isNull();
    }

    @Test
    @DisplayName("전량 대기 중인 주문은 완료 목록에 포함되지 않는다")
    void fullyPendingNotCompleted() {
        limit(sellerPlayer, OrderSide.SELL, 11_000, 10);

        assertThat(queryService.getOrders(SELLER_MEMBER, leagueId, OrderListType.COMPLETED, null))
                .isEmpty();
    }

    @Test
    @DisplayName("체결 완료·취소·만료된 주문은 완료 목록에만 포함된다")
    void filledCanceledExpiredOnlyCompleted() {
        final long filled = limit(sellerPlayer, OrderSide.SELL, 11_000, 2);
        limit(buyerPlayer, OrderSide.BUY, 11_000, 2);
        long canceled = limit(sellerPlayer, OrderSide.SELL, 12_000, 2);
        limitOrderService.cancelOrder(leagueId, sellerPlayer, canceled);
        long expired = limit(sellerPlayer, OrderSide.SELL, 13_000, 2);
        entityManager.find(Order.class, expired).expire(LocalDateTime.of(2026, 11, 16, 9, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(queryService.getOrders(SELLER_MEMBER, leagueId, OrderListType.PENDING, null))
                .isEmpty();
        assertThat(queryService.getOrders(SELLER_MEMBER, leagueId, OrderListType.COMPLETED, null))
                .extracting(OrderSummary::orderId).containsExactly(expired, canceled, filled);
    }

    @Test
    @DisplayName("거래일 변경으로 만료된 잔여 수량은 취소 수량에 합산한다")
    void expiredCountsAsCanceled() {
        long sell = limit(sellerPlayer, OrderSide.SELL, 11_000, 10);
        limit(buyerPlayer, OrderSide.BUY, 11_000, 3);
        entityManager.find(Order.class, sell).expire(LocalDateTime.of(2026, 11, 16, 9, 0));
        entityManager.flush();
        entityManager.clear();

        OrderSummary summary = queryService.getOrders(SELLER_MEMBER, leagueId,
                OrderListType.COMPLETED, null).get(0);

        assertThat(summary.filledQuantity()).isEqualTo(3L);
        assertThat(summary.canceledQuantity()).isEqualTo(7L);
        assertThat(summary.pendingQuantity()).isNull();
    }

    @Test
    @DisplayName("시장가 체결 없이 전량 취소된 주문은 완료 목록에 포함되고 체결 단가를 제공하지 않는다")
    void listMarketNotFilled() {
        long market = market(buyerPlayer, OrderSide.BUY, 10);

        List<OrderSummary> list = queryService.getOrders(BUYER_MEMBER, leagueId,
                OrderListType.COMPLETED, null);

        assertThat(list).extracting(OrderSummary::orderId).containsExactly(market);
        assertThat(list.get(0).type()).isEqualTo(OrderType.MARKET);
        assertThat(list.get(0).price()).isNull();
        assertThat(list.get(0).filledQuantity()).isNull();
        assertThat(list.get(0).canceledQuantity()).isEqualTo(10L);
        assertThat(list.get(0).executionPrice()).isNull();
        assertThat(list.get(0).averagedPrice()).isFalse();
    }

    @Test
    @DisplayName("체결 단가가 한 가지면 주당 체결 단가를, 둘 이상이면 원 미만 절사한 평균 체결 단가를 제공한다")
    void executionPrice() {
        final long single = limit(buyerPlayer, OrderSide.BUY, 10_000, 2);
        limit(sellerPlayer, OrderSide.SELL, 10_000, 2);
        limit(sellerPlayer, OrderSide.SELL, 10_000, 1);
        limit(sellerPlayer, OrderSide.SELL, 10_100, 2);
        long mixed = market(buyerPlayer, OrderSide.BUY, 3);

        List<OrderSummary> list = queryService.getOrders(BUYER_MEMBER, leagueId,
                OrderListType.COMPLETED, null);

        assertThat(list).extracting(OrderSummary::orderId).containsExactly(mixed, single);
        assertThat(list.get(0).executionPrice()).isEqualTo(10_066L);
        assertThat(list.get(0).averagedPrice()).isTrue();
        assertThat(list.get(1).executionPrice()).isEqualTo(10_000L);
        assertThat(list.get(1).averagedPrice()).isFalse();
    }

    @Test
    @DisplayName("특정 종목을 지정하면 그 종목 주문만 조회하고 리그에 없는 종목은 거절한다")
    void stockFilter() {
        long otherLister = entityManager.persistAndGetId(
                Player.enter(leagueId, 4L, NOW.minusDays(1)), Long.class);
        long otherStock = entityManager.persistAndGetId(Stock.list(leagueId, otherLister,
                StockName.of("다른종목"), StockDescription.of("소개")), Long.class);
        entityManager.persist(Holding.allocate(leagueId, sellerPlayer, otherStock, 100));
        entityManager.flush();
        entityManager.clear();
        limit(sellerPlayer, OrderSide.SELL, 11_000, 1);
        PlacedOrder other = limitOrderService.placeLimitOrder(new PlaceLimitOrder(
                leagueId, sellerPlayer, otherStock, OrderSide.SELL, 11_000, 1), BASE_PRICE);
        entityManager.flush();
        entityManager.clear();

        assertThat(queryService.getOrders(SELLER_MEMBER, leagueId, OrderListType.PENDING,
                otherStock)).extracting(OrderSummary::orderId).containsExactly(other.orderId());
        assertThat(queryService.getOrders(SELLER_MEMBER, leagueId, OrderListType.PENDING, null))
                .hasSize(2);
        assertThatThrownBy(() -> queryService.getOrders(SELLER_MEMBER, leagueId,
                OrderListType.PENDING, 9_999L))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND));
    }

    @Test
    @DisplayName("타인의 주문은 목록에 포함되지 않는다")
    void othersOrdersExcluded() {
        limit(sellerPlayer, OrderSide.SELL, 11_000, 10);

        assertThat(queryService.getOrders(BUYER_MEMBER, leagueId, OrderListType.PENDING, null))
                .isEmpty();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 주문 목록을 조회할 수 없다")
    void listNotJoined() {
        assertThatThrownBy(() -> queryService.getOrders(OUTSIDER_MEMBER, leagueId,
                OrderListType.PENDING, null))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("대기실에서는 시작 전 코드로, 종료된 리그에서는 종료 코드로 주문 목록 조회를 거절한다")
    void listNotTradable() {
        League waiting = entityManager.persistAndFlush(League.create(
                LeagueName.of("대기실"), InviteCode.of("BBBBBBB"), NOW.minusDays(1)));
        entityManager.persistAndFlush(Player.enter(waiting.getId(), SELLER_MEMBER, NOW));

        assertThatThrownBy(() -> queryService.getOrders(SELLER_MEMBER, waiting.getId(),
                OrderListType.PENDING, null))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE));

        setClock(league.getEndsAt());
        assertThatThrownBy(() -> queryService.getOrders(SELLER_MEMBER, leagueId,
                OrderListType.PENDING, null))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
    }

    private long limit(long playerId, OrderSide side, long price, long quantity) {
        PlacedOrder placed = limitOrderService.placeLimitOrder(new PlaceLimitOrder(
                leagueId, playerId, stockId, side, price, quantity), BASE_PRICE);
        entityManager.flush();
        entityManager.clear();
        return placed.orderId();
    }

    private long market(long playerId, OrderSide side, long quantity) {
        PlacedOrder placed = marketOrderService.placeMarketOrder(new PlaceMarketOrder(
                leagueId, playerId, stockId, side, quantity), BASE_PRICE);
        entityManager.flush();
        entityManager.clear();
        return placed.orderId();
    }

    private List<Long> playerIds(long targetLeagueId) {
        return entityManager.getEntityManager().createQuery(
                        "select p.id from Player p where p.leagueId = :l order by p.id", Long.class)
                .setParameter("l", targetLeagueId).getResultList();
    }

    private void setClock(LocalDateTime at) {
        ((LimitOrderServiceTest.SettableClock) clock).set(at);
    }

    private League startedLeague(String inviteCode, LocalDateTime startedAt, boolean members) {
        League created = entityManager.persistAndFlush(League.create(
                LeagueName.of("리그"), InviteCode.of(inviteCode), startedAt.minusDays(1)));
        List<Player> players = List.of(LISTER_MEMBER, SELLER_MEMBER, BUYER_MEMBER).stream()
                .map(memberId -> {
                    Player player = Player.enter(created.getId(), memberId,
                            startedAt.minusDays(1));
                    player.completeListing(startedAt.minusDays(1));
                    return entityManager.persist(player);
                })
                .toList();
        created.start(players, startedAt);
        return entityManager.persistAndFlush(created);
    }
}
