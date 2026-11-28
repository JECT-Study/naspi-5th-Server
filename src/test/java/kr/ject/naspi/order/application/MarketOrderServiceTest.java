package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.application.AssetSettlementService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.config.ClockConfig;
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
import kr.ject.naspi.market.application.StockTradingDayService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import kr.ject.naspi.order.domain.OrderType;
import kr.ject.naspi.order.domain.Trade;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 시장가 주문 접수 흐름(보호 가격 확정 → 예약 → 매칭 → 정산 → 잔량 해제)을 실제 asset·league·market
 * 서비스와 함께 확인한다 (ORD-05, INT-02).
 */
@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({MarketOrderService.class, LimitOrderService.class, OrderExecutionSupport.class,
        LeagueTradingService.class, LeagueQueryService.class, StockQueryService.class,
        StockExecutionService.class, StockTradingDayService.class, LocalStockSnapshotCache.class,
        AssetReservationService.class, AssetSettlementService.class,
        MarketOrderServiceTest.FixedClockConfig.class})
class MarketOrderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final long LISTER = 1L;
    private static final long SELLER = 20L;
    private static final long BUYER = 30L;
    private static final long POOR = 40L;
    private static final long CASH = 1_000_000;

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return new SettableClock(NOW);
        }
    }

    /** 테스트가 현재 시각을 옮길 수 있는 시계. */
    static class SettableClock extends Clock {

        private Instant instant;

        SettableClock(LocalDateTime at) {
            set(at);
        }

        void set(LocalDateTime at) {
            this.instant = at.atZone(ClockConfig.KST).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ClockConfig.KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired
    private Clock clock;

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private MarketOrderService service;

    @Autowired
    private LimitOrderService limitOrderService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private StockTradingDayService stockTradingDayService;

    private long leagueId;
    private long stockId;
    private long extraPlayers;

    @BeforeEach
    void setUp() {
        ((SettableClock) clock).set(NOW);
        leagueId = startedLeague("AAAAAAA", NOW).getId();
        Stock stock = Stock.list(leagueId, LISTER, StockName.of("종목"),
                StockDescription.of("소개"));
        stock.open();
        stockId = entityManager.persistAndGetId(stock, Long.class);
        entityManager.persist(StockDailyPrice.open(leagueId, stockId, 1, 10_000));
        for (long player : new long[] {LISTER, SELLER, BUYER}) {
            entityManager.persist(Account.open(leagueId, player, CASH));
            entityManager.persist(Holding.allocate(leagueId, player, stockId,
                    player == LISTER ? 3_000 : player == SELLER ? 100 : 0));
        }
        entityManager.persist(Account.open(leagueId, POOR, 104_990));
        entityManager.persist(Holding.allocate(leagueId, POOR, stockId, 0));
        entityManager.flush();
    }

    @Test
    @DisplayName("시장가 매수는 최우선 매도 호가보다 5% 높은 가격까지 체결하고 잔여 수량은 취소한다")
    void buyFillsWithinProtectionAndCancelsRest() {
        ask(10_000, 3);
        ask(10_500, 2);
        ask(10_600, 5);

        PlacedOrder placed = market(BUYER, OrderSide.BUY, 10);

        assertThat(placed.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(placed.remainingQuantity()).isEqualTo(5);
        Order saved = entityManager.find(Order.class, placed.orderId());
        assertThat(saved.getCancelReason()).isEqualTo(CancelReason.MARKET_PARTIALLY_FILLED);
        assertThat(holding(BUYER).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("시장가 매수는 체결·취소 처리 후 예약금 중 실제 총체결 금액을 제외한 나머지를 전액 해제한다")
    void buyReleasesRemainingReservation() {
        ask(10_000, 3);
        ask(10_500, 2);
        ask(10_600, 5);

        market(BUYER, OrderSide.BUY, 10);

        assertThat(account(BUYER).getReservedDeposit()).isZero();
        assertThat(account(BUYER).getDeposit()).isEqualTo(CASH - (30_000 + 21_000));
    }

    @Test
    @DisplayName("매수 보호 가격은 100원 미만을 절사한다")
    void buyProtectionFloorsUnder100Won() {
        ask(10_100, 1);
        ask(10_600, 1);
        ask(10_700, 1);

        PlacedOrder placed = market(BUYER, OrderSide.BUY, 3);

        // 10,100 × 1.05 = 10,605 → 10,600. 10,600까지 체결하고 10,700은 남는다
        assertThat(placed.remainingQuantity()).isEqualTo(1);
        assertThat(holding(BUYER).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("시장가 매도는 최우선 매수 호가보다 5% 낮은 가격까지 체결하고 잔여 수량의 예약 주식을 해제한다")
    void sellFillsWithinProtectionAndReleasesStock() {
        bid(10_000, 3);
        bid(9_500, 2);
        bid(9_400, 5);

        PlacedOrder placed = market(SELLER, OrderSide.SELL, 10);

        assertThat(placed.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(placed.remainingQuantity()).isEqualTo(5);
        assertThat(entityManager.find(Order.class, placed.orderId()).getCancelReason())
                .isEqualTo(CancelReason.MARKET_PARTIALLY_FILLED);
        assertThat(holding(SELLER).getQuantity()).isEqualTo(95);
        assertThat(holding(SELLER).getReservedQuantity()).isZero();
        assertThat(account(SELLER).getDeposit()).isEqualTo(CASH + 30_000 + 19_000);
    }

    @Test
    @DisplayName("매도 보호 가격은 100원 미만을 절사한다")
    void sellProtectionFloorsUnder100Won() {
        bid(9_900, 1);
        bid(9_400, 1);
        bid(9_300, 1);

        PlacedOrder placed = market(SELLER, OrderSide.SELL, 3);

        assertThat(placed.remainingQuantity()).isEqualTo(1);
        assertThat(holding(SELLER).getQuantity()).isEqualTo(98);
    }

    @Test
    @DisplayName("보호 가격이 당일 상·하한가를 벗어나면 상·하한가를 적용한다")
    void protectionClampedToPriceLimit() {
        ask(12_900, 1);
        ask(13_000, 1);

        PlacedOrder buy = market(BUYER, OrderSide.BUY, 3);

        // 12,900 × 1.05 = 13,545 → 상한가 13,000. 두 호가 모두 체결된다
        assertThat(buy.remainingQuantity()).isEqualTo(1);
        assertThat(holding(BUYER).getQuantity()).isEqualTo(2);

        bid(7_100, 1);
        bid(7_000, 1);

        PlacedOrder sell = market(SELLER, OrderSide.SELL, 3);

        assertThat(sell.remainingQuantity()).isEqualTo(1);
        // 앞선 매수 체결로 2주를 이미 팔았고, 이번에 2주를 더 판다
        assertThat(holding(SELLER).getQuantity()).isEqualTo(96);
    }

    @Test
    @DisplayName("시장가 보호 가격이 그 거래일 상·하한가를 벗어나면 상·하한가를 적용한다")
    void protectionClampedToTradingDayLimit() {
        // 1일차 8,000 체결 -> 2일차 기준가 8,000 (5,600~10,400)
        limit(SELLER, OrderSide.SELL, 8_000, 1);
        limit(BUYER, OrderSide.BUY, 8_000, 1);
        entityManager.getEntityManager().createQuery(
                "select t from TradingDay t where t.leagueId = :l and t.dayNumber = 1",
                TradingDay.class).setParameter("l", leagueId).getSingleResult()
                .close(LocalDateTime.of(2026, 11, 16, 9, 0, 0));
        stockTradingDayService.changeTradingDay(leagueId, 1);
        entityManager.flush();
        entityManager.clear();
        ((SettableClock) clock).set(LocalDateTime.of(2026, 11, 16, 9, 0, 0));
        ask(10_000, 1);

        long poorer = player(20_400, 0);

        // 10,000 × 1.05 = 10,500 → 2일차 상한가 10,400. 예약금 = 10,000 + 10,400 = 20,400이라 접수된다
        // (상한가를 적용하지 않으면 10,000 + 10,500 = 20,500이라 거절된다)
        PlacedOrder buy = market(poorer, OrderSide.BUY, 2);

        assertThat(buy.quantity()).isEqualTo(2);
        assertThat(buy.remainingQuantity()).isEqualTo(1);
        assertThat(account(poorer).getReservedDeposit()).isZero();
        assertThat(account(poorer).getDeposit()).isEqualTo(10_400);
    }

    @Test
    @DisplayName("09:00 이후 거래일 전환 처리 전 시장가 주문은 거절된다")
    void rejectMarketOrderBeforeChange() {
        ((SettableClock) clock).set(LocalDateTime.of(2026, 11, 16, 9, 0, 0));

        assertThatThrownBy(() -> market(BUYER, OrderSide.BUY, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRADING_DAY_CHANGE_PENDING);
    }

    @Test
    @DisplayName("보호 범위는 접수 시 확정되어 체결로 최우선 호가가 바뀌어도 넓어지지 않는다")
    void protectionFixedAtReceipt() {
        ask(10_000, 1);
        ask(10_500, 1);
        ask(10_600, 1);

        PlacedOrder placed = market(BUYER, OrderSide.BUY, 3);

        assertThat(placed.remainingQuantity()).isEqualTo(1);
        assertThat(holding(BUYER).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("시장가 주문이 전량 체결되면 체결 완료가 되고 예약금이 남지 않는다")
    void fullyFilled() {
        ask(10_000, 3);
        ask(10_500, 2);

        PlacedOrder placed = market(BUYER, OrderSide.BUY, 5);

        assertThat(placed.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(placed.remainingQuantity()).isZero();
        assertThat(entityManager.find(Order.class, placed.orderId()).getCancelReason()).isNull();
        assertThat(account(BUYER).getReservedDeposit()).isZero();
        assertThat(account(BUYER).getDeposit()).isEqualTo(CASH - 51_000);
    }

    @Test
    @DisplayName("시장가 주문이 내 주문과 체결될 차례가 되면 내 주문 간 체결 방지로 취소하고 예약을 해제한다")
    void selfTradeCancelsAndReleases() {
        ask(10_000, 5);

        PlacedOrder placed = market(SELLER, OrderSide.BUY, 5);

        assertThat(placed.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(entityManager.find(Order.class, placed.orderId()).getCancelReason())
                .isEqualTo(CancelReason.SELF_TRADE_PREVENTED);
        assertThat(account(SELLER).getReservedDeposit()).isZero();
        assertThat(account(SELLER).getDeposit()).isEqualTo(CASH);
        assertThat(events.stream(TradeExecuted.class)).isEmpty();
    }

    @Test
    @DisplayName("상대 호가가 없으면 체결 가능 수량 없음으로 전량 취소하고 자산을 예약하지 않는다")
    void noOppositeQuote() {
        PlacedOrder buy = market(POOR, OrderSide.BUY, 10);
        PlacedOrder sell = market(SELLER, OrderSide.SELL, 10);

        for (PlacedOrder placed : List.of(buy, sell)) {
            assertThat(placed.status()).isEqualTo(OrderStatus.CANCELED);
            assertThat(placed.remainingQuantity()).isEqualTo(10);
            Order saved = entityManager.find(Order.class, placed.orderId());
            assertThat(saved.getCancelReason()).isEqualTo(CancelReason.MARKET_NOT_FILLED);
            assertThat(saved.getCanceledAt()).isEqualTo(NOW);
        }
        assertThat(entityManager.find(Order.class, buy.orderId()).getReservedPrice()).isNull();
        assertThat(account(POOR).getReservedDeposit()).isZero();
        assertThat(holding(SELLER).getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("시장가 매수 예약금은 보호 범위 안 매도 호가를 최우선부터 주문 수량까지 합산하고, 부족한 수량은 보호최고가로 계산한다")
    void buyReservationSumsQuotesThenProtectionPrice() {
        ask(10_000, 10);
        long exact = player(100_000, 0);

        // 10,000 × 10 = 100,000. 보호최고가(10,500) × 10 = 105,000이 아니어도 주문된다
        PlacedOrder placed = market(exact, OrderSide.BUY, 10);

        assertThat(placed.quantity()).isEqualTo(10);
        assertThat(placed.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(account(exact).getDeposit()).isZero();
        assertThat(account(exact).getReservedDeposit()).isZero();
    }

    @Test
    @DisplayName("시장가 매수 예약금이 주문 가능 현금보다 많으면 수량을 줄이지 않고 주문을 거절한다")
    void rejectWhenCashShort() {
        ask(10_000, 7);
        long poorer = player(70_000, 0);

        assertThatThrownBy(() -> market(poorer, OrderSide.BUY, 8))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_DEPOSIT);
        assertThat(marketOrderCount()).isZero();
        assertThat(account(poorer).getReservedDeposit()).isZero();
        assertThat(account(poorer).getDeposit()).isEqualTo(70_000);
    }

    @Test
    @DisplayName("호가가 모자란 수량은 보호최고가로 예약하고, 체결하지 못한 수량은 취소한 뒤 남은 예약금을 전액 해제한다")
    void missingQuotesReservedAtProtectionPrice() {
        ask(10_000, 3);
        long buyer = player(51_000, 0);

        // 30,000 + 10,500 × 2 = 51,000
        PlacedOrder placed = market(buyer, OrderSide.BUY, 5);

        assertThat(placed.quantity()).isEqualTo(5);
        assertThat(placed.remainingQuantity()).isEqualTo(2);
        assertThat(entityManager.find(Order.class, placed.orderId()).getCancelReason())
                .isEqualTo(CancelReason.MARKET_PARTIALLY_FILLED);
        assertThat(account(buyer).getReservedDeposit()).isZero();
        assertThat(account(buyer).getDeposit()).isEqualTo(21_000);
    }

    @Test
    @DisplayName("호가가 모자란 수량까지 보호최고가로 예약할 현금이 없으면 시장가 매수를 거절한다")
    void rejectWhenCashShortForMissingQuotes() {
        ask(10_000, 3);
        long buyer = player(50_999, 0);

        assertThatThrownBy(() -> market(buyer, OrderSide.BUY, 5))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_DEPOSIT);
        assertThat(marketOrderCount()).isZero();
    }

    @Test
    @DisplayName("주문 가능 현금은 대기 중인 매수 주문의 예약금을 뺀 금액이다")
    void orderableDepositExcludesReserved() {
        ask(10_000, 10);
        long poorer = player(50_000, 0);
        limit(poorer, OrderSide.BUY, 9_000, 3);

        // 50,000 - 27,000 = 23,000: 2주는 되고 3주는 거절
        assertThatThrownBy(() -> market(poorer, OrderSide.BUY, 3))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_DEPOSIT);
        PlacedOrder placed = market(poorer, OrderSide.BUY, 2);

        assertThat(placed.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(account(poorer).getReservedDeposit()).isEqualTo(27_000);
        assertThat(account(poorer).getDeposit()).isEqualTo(30_000);
    }

    @Test
    @DisplayName("시장가 매도 수량이 매도 가능 수량보다 많으면 수량을 줄이지 않고 주문을 거절한다")
    void rejectWhenSellQuantityExceedsSellable() {
        bid(10_000, 10);
        long few = player(0, 5);

        assertThatThrownBy(() -> market(few, OrderSide.SELL, 8))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);
        assertThat(marketOrderCount()).isZero();
        assertThat(holding(few).getQuantity()).isEqualTo(5);
        assertThat(holding(few).getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("상장자의 시장가 매도가 자사주 최소 유지분 1,000주를 남기지 못하면 주문을 거절한다")
    void rejectListerSellBelowMinimumShares() {
        bid(10_000, 50);
        limit(LISTER, OrderSide.SELL, 13_000, 1_990);

        // 3,000 - 1,990(예약) - 1,000(최소 유지분) = 10주까지 가능
        assertThatThrownBy(() -> market(LISTER, OrderSide.SELL, 11))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TREASURY_STOCK_MIN_SHARES);
        assertThat(marketOrderCount()).isZero();
        assertThat(holding(LISTER).getReservedQuantity()).isEqualTo(1_990);
    }

    @Test
    @DisplayName("매도 가능 수량이 없으면 시장가 매도를 거절하고 주문을 남기지 않는다")
    void rejectWhenNoSellableQuantity() {
        bid(10_000, 1);

        assertThatThrownBy(() -> market(POOR, OrderSide.SELL, 1))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_QUANTITY);
        assertThat(marketOrderCount()).isZero();
    }

    @Test
    @DisplayName("주문 수량이 1주 미만이면 거절한다")
    void rejectInvalidQuantity() {
        assertThatThrownBy(() -> market(BUYER, OrderSide.BUY, 0))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_QUANTITY);
    }

    @Test
    @DisplayName("리그에 없는 종목은 시장가 주문을 거절한다")
    void rejectStockNotInLeague() {
        assertThatThrownBy(() -> service.placeMarketOrder(
                new PlaceMarketOrder(leagueId, BUYER, 9_999L, OrderSide.BUY, 1)))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("거래 가능 상태가 아닌 리그와 종료된 리그에서는 시장가 주문을 거절한다")
    void rejectNotTradableLeague() {
        League waiting = entityManager.persistAndFlush(League.create(
                LeagueName.of("대기실"), InviteCode.of("BBBBBBB"), NOW.minusDays(1)));
        League ended = startedLeague("DDDDDDD", NOW.minusDays(8));

        assertThatThrownBy(() -> service.placeMarketOrder(
                new PlaceMarketOrder(waiting.getId(), BUYER, stockId, OrderSide.BUY, 1)))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE);
        assertThatThrownBy(() -> service.placeMarketOrder(
                new PlaceMarketOrder(ended.getId(), BUYER, stockId, OrderSide.BUY, 1)))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("시장가 체결도 체결 1건마다 체결 기록을 저장하고 체결 이벤트를 발행한다")
    void recordsTradesAndPublishesEvents() {
        ask(10_000, 3);
        ask(10_500, 2);

        market(BUYER, OrderSide.BUY, 5);

        List<Trade> trades = entityManager.getEntityManager()
                .createQuery("select t from Trade t where t.leagueId = :l order by t.id",
                        Trade.class)
                .setParameter("l", leagueId).getResultList();
        assertThat(trades).hasSize(2);
        assertThat(trades).extracting(Trade::getPrice).containsExactly(10_000L, 10_500L);
        assertThat(trades).extracting(Trade::getTradingDay).containsOnly(1);
        assertThat(events.stream(TradeExecuted.class).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("시장가 체결 시에도 현재가는 마지막 체결가, 거래량은 체결 수량의 합이 된다")
    void marketTradeUpdatesCurrentPriceAndVolume() {
        ask(10_000, 3);
        ask(10_500, 2);

        market(BUYER, OrderSide.BUY, 5);

        assertThat(currentPrice()).isEqualTo(10_500);
        assertThat(volume()).isEqualTo(5);
    }

    @Test
    @DisplayName("시장가 주문이 체결되지 않으면 현재가와 거래량은 바뀌지 않는다")
    void noOppositeQuoteKeepsPrice() {
        market(BUYER, OrderSide.BUY, 5);

        assertThat(currentPrice()).isEqualTo(10_000);
        assertThat(entityManager.getEntityManager().createQuery(
                "select sum(d.volume) from StockDailyPrice d", Long.class).getSingleResult())
                .isZero();
    }

    private long currentPrice() {
        return entityManager.find(Stock.class, stockId).getCurrentPrice();
    }

    private long volume() {
        return entityManager.getEntityManager().createQuery(
                        "select d.volume from StockDailyPrice d where d.stockId = :s", Long.class)
                .setParameter("s", stockId).getSingleResult();
    }

    private PlacedOrder market(long playerId, OrderSide side, long quantity) {
        PlacedOrder placed = service.placeMarketOrder(
                new PlaceMarketOrder(leagueId, playerId, stockId, side, quantity));
        entityManager.flush();
        entityManager.clear();
        return placed;
    }

    private void ask(long price, long quantity) {
        limit(SELLER, OrderSide.SELL, price, quantity);
    }

    private void bid(long price, long quantity) {
        limit(BUYER, OrderSide.BUY, price, quantity);
    }

    private void limit(long playerId, OrderSide side, long price, long quantity) {
        limitOrderService.placeLimitOrder(new PlaceLimitOrder(
                leagueId, playerId, stockId, side, price, quantity));
        entityManager.flush();
        entityManager.clear();
    }

    /** 리그에 계좌와 보유 주식이 있는 플레이어를 추가한다. */
    private long player(long cash, long quantity) {
        long playerId = 100 + extraPlayers++;
        entityManager.persist(Account.open(leagueId, playerId, cash));
        entityManager.persist(Holding.allocate(leagueId, playerId, stockId, quantity));
        entityManager.flush();
        return playerId;
    }

    private long marketOrderCount() {
        return entityManager.getEntityManager()
                .createQuery("select count(o) from Order o where o.type = :t", Long.class)
                .setParameter("t", OrderType.MARKET).getSingleResult();
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

    private League startedLeague(String inviteCode, LocalDateTime startedAt) {
        League league = entityManager.persistAndFlush(League.create(
                LeagueName.of("리그"), InviteCode.of(inviteCode), startedAt.minusDays(1)));
        List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(league.getId(), memberId, startedAt.minusDays(1));
                    player.completeListing(startedAt.minusDays(1));
                    return player;
                })
                .toList();
        league.start(players, startedAt);
        TradingDay.of(league.getId(), league.tradingSchedule()).forEach(entityManager::persist);
        return entityManager.persistAndFlush(league);
    }
}
