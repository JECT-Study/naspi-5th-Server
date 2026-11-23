package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import kr.ject.naspi.order.domain.Trade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 리그에 주문·취소가 동시에 몰려도 리그 거래 락으로 차례로 처리되어 체결 순서와 자산 정합성이 지켜지는지
 * 검증한다 (ORD-06). 각 스레드가 자기 트랜잭션을 커밋해야 하므로 테스트 트랜잭션을 쓰지 않는다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import({LimitOrderService.class, OrderExecutionSupport.class,
        LeagueTradingService.class, LeagueQueryService.class, StockQueryService.class,
        StockExecutionService.class, LocalStockSnapshotCache.class, AssetReservationService.class,
        AssetSettlementService.class, LimitOrderConcurrencyTest.FixedClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LimitOrderConcurrencyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final long LISTER = 1L;
    private static final long SELLER = 20L;
    private static final long BUYER = 30L;
    private static final List<Long> PLAYERS = List.of(LISTER, SELLER, BUYER);
    private static final long INITIAL_DEPOSIT = 1_000_000;
    private static final int REQUESTS = 10;

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(ClockConfig.KST).toInstant(), ClockConfig.KST);
        }
    }

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private LimitOrderService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private long leagueId;
    private long stockId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            League league = League.create(LeagueName.of("리그"), InviteCode.of("DDDDDDD"),
                    NOW.minusDays(1));
            entityManager.persist(league);
            entityManager.flush();
            List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                    .mapToObj(memberId -> {
                        Player player = Player.enter(league.getId(), memberId, NOW.minusDays(1));
                        player.completeListing(NOW.minusDays(1));
                        return player;
                    })
                    .toList();
            league.start(players, NOW);
            leagueId = league.getId();
            TradingDay.of(leagueId, league.tradingSchedule()).forEach(entityManager::persist);

            Stock stock = Stock.list(leagueId, LISTER, StockName.of("종목"),
                    StockDescription.of("소개"));
            stock.open();
            entityManager.persist(stock);
            entityManager.flush();
            stockId = stock.getId();
            entityManager.persist(StockDailyPrice.open(leagueId, stockId, 1, 10_000));
            for (long player : PLAYERS) {
                entityManager.persist(Account.open(leagueId, player, INITIAL_DEPOSIT));
                entityManager.persist(Holding.allocate(leagueId, player, stockId,
                        player == LISTER ? 3_000 : player == SELLER ? 1_000 : 0));
            }
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from orders").executeUpdate();
            entityManager.createQuery("delete from Account").executeUpdate();
            entityManager.createQuery("delete from Holding").executeUpdate();
            entityManager.createQuery("delete from Stock").executeUpdate();
            entityManager.createQuery("delete from StockDailyPrice").executeUpdate();
            entityManager.createQuery("delete from Trade").executeUpdate();
            entityManager.createQuery("delete from Player").executeUpdate();
            entityManager.createQuery("delete from TradingDay").executeUpdate();
            entityManager.createQuery("delete from League").executeUpdate();
        });
    }

    @Test
    @DisplayName("같은 참여자의 매수 주문이 동시에 몰려도 주문 가능 현금을 넘어 예약되지 않는다")
    void concurrentBuyOrdersDoNotOverReserve() throws Exception {
        // 100만 원으로 30만 원(10,000원 × 30주) 주문 10건 → 3건만 접수
        List<Boolean> results = runConcurrently(Collections.nCopies(REQUESTS,
                () -> place(BUYER, OrderSide.BUY, 10_000, 30)));

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(3);
        assertThat(account(BUYER).getReservedDeposit()).isEqualTo(900_000);
        assertReservationsMatchOpenOrders();
    }

    @Test
    @DisplayName("같은 주문의 취소가 동시에 들어와도 한 번만 취소되고 예약은 한 번만 해제된다")
    void concurrentCancelsReleaseOnce() throws Exception {
        long orderId = place(BUYER, OrderSide.BUY, 9_000, 10);
        place(BUYER, OrderSide.BUY, 9_000, 10);

        List<Boolean> results = runConcurrently(Collections.nCopies(REQUESTS,
                () -> service.cancelOrder(leagueId, BUYER, orderId)));

        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(account(BUYER).getReservedDeposit()).isEqualTo(90_000);
        assertReservationsMatchOpenOrders();
    }

    @Test
    @DisplayName("취소와 체결이 동시에 요청되면 먼저 확정된 처리 결과가 적용된다")
    void cancelAndFillRaceAppliesFirstConfirmedResult() throws Exception {
        for (int round = 0; round < REQUESTS; round++) {
            long sellOrderId = place(SELLER, OrderSide.SELL, 11_000, 10);
            long[] buyOrderId = new long[1];

            List<Boolean> results = runConcurrently(List.of(
                    () -> service.cancelOrder(leagueId, SELLER, sellOrderId),
                    () -> buyOrderId[0] = place(BUYER, OrderSide.BUY, 11_000, 10)));

            boolean canceled = results.get(0);
            assertThat(results.get(1)).as("매수 주문은 항상 접수된다").isTrue();
            if (canceled) {
                // 취소가 먼저 확정: 매도 주문은 체결 없이 취소되고 매수 주문은 대기로 남는다.
                assertThat(order(sellOrderId).filledQuantity()).isZero();
                assertThat(order(buyOrderId[0]).getStatus()).isEqualTo(OrderStatus.PENDING);
                service.cancelOrder(leagueId, BUYER, buyOrderId[0]);
            } else {
                // 체결이 먼저 확정: 매도 주문은 전량 체결되어 취소는 상태 오류로 거절된다.
                assertThat(order(sellOrderId).getStatus()).isEqualTo(OrderStatus.FILLED);
                assertThat(order(buyOrderId[0]).getStatus()).isEqualTo(OrderStatus.FILLED);
            }
            assertReservationsMatchOpenOrders();
        }
        assertTotalsPreserved();
    }

    @Test
    @DisplayName("여러 참여자의 주문과 취소가 동시에 몰려도 현금·주식 총량이 보존되고 예약은 미체결 주문과 같다")
    void mixedOrdersAndCancelsKeepAssetsConsistent() throws Exception {
        List<Long> resting = new ArrayList<>();
        for (long price : new long[] {10_000, 10_100, 10_200}) {
            resting.add(place(SELLER, OrderSide.SELL, price, 20));
            resting.add(place(LISTER, OrderSide.SELL, price, 20));
            resting.add(place(BUYER, OrderSide.BUY, price - 300, 20));
        }
        List<Callable<?>> requests = new ArrayList<>();
        IntStream.range(0, REQUESTS).forEach(i -> {
            long price = 9_800 + (i % 5) * 100;
            requests.add(() -> place(BUYER, OrderSide.BUY, price, 15));
            requests.add(() -> place(SELLER, OrderSide.SELL, price, 15));
            requests.add(() -> place(LISTER, OrderSide.BUY, price, 5));
        });
        for (int i = 0; i < resting.size(); i++) {
            long orderId = resting.get(i);
            long owner = i % 3 == 0 ? SELLER : i % 3 == 1 ? LISTER : BUYER;
            requests.add(() -> service.cancelOrder(leagueId, owner, orderId));
        }

        runConcurrently(requests);

        assertReservationsMatchOpenOrders();
        assertTotalsPreserved();
        assertVolumeAndPriceMatchTrades();
    }

    /** 주문을 접수하고 주문 ID를 돌려준다. */
    private long place(long playerId, OrderSide side, long price, long quantity) {
        return service.placeLimitOrder(new PlaceLimitOrder(leagueId, playerId, stockId, side,
                price, quantity)).orderId();
    }

    /**
     * 요청을 동시에 실행하고 요청마다 비즈니스 오류 없이 끝났는지를 순서대로 돌려준다. 비즈니스 오류가 아닌
     * 예외(데이터 오류, 데드락)는 테스트를 실패시킨다.
     */
    private List<Boolean> runConcurrently(List<? extends Callable<?>> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<?> request : requests) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    request.call();
                    return true;
                } catch (NaspiBizException e) {
                    assertThat(e.getErrorCode()).isIn(ErrorCode.INSUFFICIENT_DEPOSIT,
                            ErrorCode.INSUFFICIENT_QUANTITY, ErrorCode.TREASURY_STOCK_MIN_SHARES,
                            ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
                    return false;
                }
            }));
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    /** 참여자마다 예약 현금·예약 수량이 미체결 주문의 잔여 수량에 묶인 값과 같다. */
    private void assertReservationsMatchOpenOrders() {
        List<Order> open = orders().stream().filter(o -> o.getStatus().isOpen()).toList();
        for (long player : PLAYERS) {
            long reservedCash = open.stream()
                    .filter(o -> o.getPlayerId() == player && o.getSide() == OrderSide.BUY)
                    .mapToLong(Order::reservedCash).sum();
            long reservedQuantity = open.stream()
                    .filter(o -> o.getPlayerId() == player && o.getSide() == OrderSide.SELL)
                    .mapToLong(Order::getRemainingQuantity).sum();
            assertThat(account(player).getReservedDeposit()).as("플레이어 %d 예약 현금", player)
                    .isEqualTo(reservedCash);
            assertThat(holding(player).getReservedQuantity()).as("플레이어 %d 예약 수량", player)
                    .isEqualTo(reservedQuantity);
        }
    }

    /** 체결은 참여자끼리 현금과 주식을 주고받을 뿐이므로 리그 전체 현금·주식 총량은 처음과 같다. */
    private void assertTotalsPreserved() {
        long deposit = PLAYERS.stream().mapToLong(p -> account(p).getDeposit()).sum();
        long quantity = PLAYERS.stream().mapToLong(p -> holding(p).getQuantity()).sum();
        assertThat(deposit).isEqualTo(INITIAL_DEPOSIT * PLAYERS.size());
        assertThat(quantity).isEqualTo(3_000 + 1_000);
    }

    /** 거래량은 체결 기록의 수량 합계와 같고, 현재가는 마지막 체결(ID가 가장 큰 체결 기록)의 가격이다. */
    private void assertVolumeAndPriceMatchTrades() {
        List<Trade> trades = tx.execute(status -> entityManager.createQuery(
                        "select t from Trade t where t.stockId = :s order by t.id", Trade.class)
                .setParameter("s", stockId).getResultList());
        assertThat(trades).as("혼합 시나리오에서 체결이 발생한다").isNotEmpty();
        long tradeQuantity = trades.stream().mapToLong(Trade::getQuantity).sum();
        Long volume = tx.execute(status -> entityManager.createQuery(
                        "select sum(d.volume) from StockDailyPrice d where d.stockId = :s",
                        Long.class)
                .setParameter("s", stockId).getSingleResult());
        Long currentPrice = tx.execute(status ->
                entityManager.find(Stock.class, stockId).getCurrentPrice());
        assertThat(volume).isEqualTo(tradeQuantity);
        assertThat(currentPrice).isEqualTo(trades.get(trades.size() - 1).getPrice());
    }

    private List<Order> orders() {
        return tx.execute(status -> entityManager.createQuery(
                        "select o from Order o where o.leagueId = :l", Order.class)
                .setParameter("l", leagueId).getResultList());
    }

    private Order order(long orderId) {
        return tx.execute(status -> entityManager.find(Order.class, orderId));
    }

    private Account account(long playerId) {
        return tx.execute(status -> entityManager.createQuery(
                        "select a from Account a where a.leagueId = :l and a.playerId = :p",
                        Account.class)
                .setParameter("l", leagueId).setParameter("p", playerId).getSingleResult());
    }

    private Holding holding(long playerId) {
        return tx.execute(status -> entityManager.createQuery(
                        "select h from Holding h where h.leagueId = :l and h.playerId = :p",
                        Holding.class)
                .setParameter("l", leagueId).setParameter("p", playerId).getSingleResult());
    }
}
