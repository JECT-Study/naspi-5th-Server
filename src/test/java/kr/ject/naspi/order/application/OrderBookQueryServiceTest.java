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
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.market.application.StockExecutionService;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
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
@Import({OrderBookQueryService.class, LimitOrderService.class, MarketOrderService.class,
        OrderExecutionSupport.class, LeagueTradingService.class, LeagueQueryService.class,
        StockQueryService.class, StockExecutionService.class, LocalStockSnapshotCache.class,
        AssetReservationService.class, AssetSettlementService.class,
        OrderBookQueryServiceTest.ClockConfig.class})
class OrderBookQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);
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
    private OrderBookQueryService queryService;

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
        Stock stock = Stock.list(leagueId, listerPlayer, StockName.of("종목"),
                StockDescription.of("소개"));
        stock.open();
        stockId = entityManager.persistAndGetId(stock, Long.class);
        entityManager.persist(StockDailyPrice.open(leagueId, stockId, 1, 10_000));
        for (long player : players) {
            entityManager.persist(Account.open(leagueId, player, 1_000_000));
            entityManager.persist(Holding.allocate(leagueId, player, stockId,
                    player == listerPlayer ? 3_000 : player == sellerPlayer ? 100 : 0));
        }
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("진행 중인 리그의 참여자는 종목의 호가와 현재가를 조회한다")
    void participantGetsOrderBook() {
        limit(sellerPlayer, OrderSide.SELL, 10_300, 5);
        limit(sellerPlayer, OrderSide.SELL, 10_100, 3);
        limit(sellerPlayer, OrderSide.SELL, 10_100, 4);
        limit(buyerPlayer, OrderSide.BUY, 9_700, 2);
        limit(buyerPlayer, OrderSide.BUY, 9_900, 6);

        OrderBookView view = queryService.getOrderBook(BUYER_MEMBER, leagueId, stockId);

        assertThat(view.currentPrice()).isEqualTo(10_000);
        assertThat(view.asks()).containsExactly(
                new OrderBookView.PriceLevel(10_100, 7), new OrderBookView.PriceLevel(10_300, 5));
        assertThat(view.bids()).containsExactly(
                new OrderBookView.PriceLevel(9_900, 6), new OrderBookView.PriceLevel(9_700, 2));
    }

    @Test
    @DisplayName("체결 완료·취소·만료 주문은 호가에 포함되지 않고 부분 체결은 잔여 수량만 반영한다")
    void excludesNonRestingOrders() {
        limit(sellerPlayer, OrderSide.SELL, 10_100, 10);
        limit(buyerPlayer, OrderSide.BUY, 10_100, 4);
        long canceled = limit(sellerPlayer, OrderSide.SELL, 10_200, 5);
        entityManager.getEntityManager().createQuery(
                "update Order o set o.status = kr.ject.naspi.order.domain.OrderStatus.CANCELED"
                        + " where o.id = :id").setParameter("id", canceled).executeUpdate();
        entityManager.clear();

        OrderBookView view = queryService.getOrderBook(SELLER_MEMBER, leagueId, stockId);

        assertThat(view.asks()).containsExactly(new OrderBookView.PriceLevel(10_100, 6));
        assertThat(view.bids()).isEmpty();
    }

    @Test
    @DisplayName("호가는 매도·매수 각각 최대 5단계까지만 제공한다")
    void limitedToFiveLevels() {
        for (int i = 0; i < 6; i++) {
            limit(sellerPlayer, OrderSide.SELL, 10_100 + i * 100L, 1);
        }

        assertThat(queryService.getOrderBook(SELLER_MEMBER, leagueId, stockId).asks())
                .hasSize(5);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 호가를 조회할 수 없다")
    void nonParticipantRejected() {
        assertThatThrownBy(() -> queryService.getOrderBook(OUTSIDER_MEMBER, leagueId, stockId))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("대기실 리그의 호가는 조회할 수 없다")
    void waitingRoomRejected() {
        League waiting = entityManager.persistAndFlush(League.create(
                LeagueName.of("대기실"), InviteCode.of("BBBBBBB"), NOW.minusDays(1)));
        entityManager.persistAndFlush(Player.enter(waiting.getId(), SELLER_MEMBER, NOW));

        assertThatThrownBy(() -> queryService.getOrderBook(SELLER_MEMBER, waiting.getId(),
                stockId))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE));
    }

    @Test
    @DisplayName("종료 시각이 지난 리그의 호가는 조회할 수 없다")
    void endedLeagueRejected() {
        setClock(league.getEndsAt());

        assertThatThrownBy(() -> queryService.getOrderBook(SELLER_MEMBER, leagueId, stockId))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
    }

    @Test
    @DisplayName("리그에 없는 종목의 호가는 조회할 수 없다")
    void otherLeagueStockRejected() {
        assertThatThrownBy(() -> queryService.getOrderBook(SELLER_MEMBER, leagueId,
                stockId + 1000))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND));
    }

    @Test
    @DisplayName("참여자 확인 없이 조회하는 진입점도 거래 가능 리그와 종목 소속을 확인한다")
    void withoutMemberCheck() {
        assertThat(queryService.getOrderBook(leagueId, stockId).currentPrice())
                .isEqualTo(10_000);
        assertThatThrownBy(() -> queryService.getOrderBook(leagueId, stockId + 1000))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND));
    }

    private long limit(long playerId, OrderSide side, long price, long quantity) {
        PlacedOrder placed = limitOrderService.placeLimitOrder(new PlaceLimitOrder(
                leagueId, playerId, stockId, side, price, quantity));
        entityManager.flush();
        entityManager.clear();
        return placed.orderId();
    }

    private long market(long playerId, OrderSide side, long quantity) {
        PlacedOrder placed = marketOrderService.placeMarketOrder(new PlaceMarketOrder(
                leagueId, playerId, stockId, side, quantity));
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
        TradingDay.of(created.getId(), created.tradingSchedule()).forEach(entityManager::persist);
        return entityManager.persistAndFlush(created);
    }
}
