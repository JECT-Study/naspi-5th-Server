package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.application.AssetSettlementService;
import kr.ject.naspi.asset.application.AssetWithdrawalService;
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
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import org.junit.jupiter.api.AfterEach;
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
 * 회원 탈퇴의 미체결 주문 취소(ORD-11)가 사용자 취소·체결·주문 접수·다른 회원의 탈퇴와 동시에 처리되어도
 * 예약이 한 번만 해제되고 교착이 없는지 검증한다. 탈퇴 쪽은 UseCase 순서(주문 취소 → 현금 폐기)를 한 트랜잭션으로
 * 흉내 낸다. 각 스레드가 자기 트랜잭션을 커밋해야 하므로 테스트 트랜잭션을 쓰지 않는다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import({LimitOrderService.class, OrderExecutionSupport.class, OpenOrderClosingService.class,
        LeagueTradingService.class, LeagueQueryService.class, StockQueryService.class,
        StockExecutionService.class, LocalStockSnapshotCache.class, AssetReservationService.class,
        AssetSettlementService.class, AssetWithdrawalService.class,
        MemberWithdrawalOrderConcurrencyTest.FixedClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MemberWithdrawalOrderConcurrencyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final int ROUNDS = 10;

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
    private LimitOrderService limitOrderService;

    @Autowired
    private OpenOrderClosingService closingService;

    @Autowired
    private AssetWithdrawalService withdrawalService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private int sequence;

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
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

    /**
     * 리그의 플레이어와 종목. {@code first}·{@code second}는 요청한 회원의 플레이어 ID다.
     */
    private record Fixture(long leagueId, long stockId, long first, long second, long lister) {
    }

    @Test
    @DisplayName("탈퇴 취소와 사용자 취소가 동시에 처리되어도 주문은 한 번만 취소되고 예약은 한 번만 해제된다")
    void withdrawalAndUserCancelReleaseOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long member = memberId(round);
            Fixture fixture = newLeague(member, member + 1);
            long orderId = placeBuy(fixture, fixture.first, 10);

            List<Object> results = runConcurrently(List.of(
                    () -> withdraw(member),
                    () -> limitOrderService.cancelOrder(fixture.leagueId, fixture.first, orderId)));

            Order order = order(orderId);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            boolean withdrawalWon = results.get(0).equals(1);
            if (withdrawalWon) {
                assertThat(results.get(1)).isInstanceOfSatisfying(NaspiBizException.class, e ->
                        assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION));
                assertThat(order.getCancelReason()).isEqualTo(CancelReason.MEMBER_WITHDRAWN);
            } else {
                assertThat(results.get(0)).isEqualTo(0);
                assertThat(results.get(1)).isNotInstanceOf(Throwable.class);
                assertThat(order.getCancelReason()).isEqualTo(CancelReason.USER_REQUESTED);
            }
            assertThat(account(fixture, fixture.first).getReservedDeposit()).isZero();
        }
    }

    @Test
    @DisplayName("탈퇴 취소와 상대 주문 체결이 동시에 일어나도 예약이 이중 해제되지 않는다")
    void withdrawalAndMatchingKeepReservationConsistent() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long member = memberId(round);
            Fixture fixture = newLeague(member, member + 1);
            long sellId = placeSell(fixture, fixture.first, 10);

            List<Object> results = runConcurrently(List.of(
                    () -> withdraw(member),
                    () -> limitOrderService.placeLimitOrder(new PlaceLimitOrder(fixture.leagueId,
                            fixture.second, fixture.stockId, OrderSide.BUY, 9_000, 5))));

            for (Object result : results) {
                assertThat(result).isNotInstanceOf(Throwable.class);
            }
            Order sell = order(sellId);
            assertThat(sell.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(sell.filledQuantity() + sell.canceledQuantity()).isEqualTo(10);
            assertThat(sell.filledQuantity()).isIn(0L, 5L);
            assertThat(holding(fixture, fixture.first).getReservedQuantity()).isZero();
            // 매수자 예약금 = 열린 매수 주문의 reservedPrice × 미체결 잔량
            long openBuyReserved = openBuyReservation(fixture, fixture.second);
            assertThat(account(fixture, fixture.second).getReservedDeposit())
                    .isEqualTo(openBuyReserved);
        }
    }

    @Test
    @DisplayName("탈퇴 처리 중 같은 플레이어가 넣은 주문은 탈퇴 커밋을 기다린 뒤 거절되어 현금 폐기가 실패하지 않는다")
    void placementDuringWithdrawalIsRejected() throws Exception {
        long member = memberId(0);
        Fixture fixture = newLeague(member, member + 1);
        placeBuy(fixture, fixture.first, 10);
        CountDownLatch locked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> withdrawal = executor.submit(() -> tx.executeWithoutResult(status -> {
            closingService.cancelAllOfMember(member);
            withdrawalService.discardCashOfMember(member);
            locked.countDown();
            // 리그 락을 쥔 채 커밋을 늦춘다
            sleep(500);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Throwable placement = catchThrowable(() -> limitOrderService.placeLimitOrder(
                new PlaceLimitOrder(fixture.leagueId, fixture.first, fixture.stockId,
                        OrderSide.BUY, 9_000, 1)));
        withdrawal.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(placement).isInstanceOfSatisfying(NaspiBizException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WITHDRAWN_PLAYER));
        Account account = account(fixture, fixture.first);
        assertThat(account.getReservedDeposit()).isZero();
        assertThat(account.getDeposit()).isZero();
    }

    @Test
    @DisplayName("같은 리그들에 참여한 두 회원이 동시에 탈퇴해도 교착 없이 처리된다")
    void concurrentWithdrawalsDoNotDeadlock() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long memberA = memberId(round);
            long memberB = memberA + 1;
            // 리그마다 플레이어 입장 순서를 엇갈리게 한다
            Fixture leagueA = newLeague(memberA, memberB);
            Fixture leagueB = newLeague(memberB, memberA);
            placeBuy(leagueA, leagueA.first, 10);
            placeBuy(leagueA, leagueA.second, 10);
            placeBuy(leagueB, leagueB.first, 10);
            placeBuy(leagueB, leagueB.second, 10);

            List<Object> results = runConcurrently(List.of(
                    () -> withdraw(memberA), () -> withdraw(memberB)));

            assertThat(results).containsExactly(2, 2);
            assertThat(account(leagueA, leagueA.first).getReservedDeposit()).isZero();
            assertThat(account(leagueB, leagueB.first).getReservedDeposit()).isZero();
        }
    }

    private long memberId(int round) {
        return 1_000L + round * 10L;
    }

    /** UseCase 순서를 흉내 낸 탈퇴 트랜잭션. 취소한 주문 수를 돌려준다. */
    private int withdraw(long memberId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            int canceled = closingService.cancelAllOfMember(memberId);
            withdrawalService.discardCashOfMember(memberId);
            return canceled;
        });
    }

    /** 리그를 시작하고 종목·자산을 준비한다. 계좌의 플레이어 ID는 전역 유일이라 호출마다 새 플레이어를 만든다. */
    private Fixture newLeague(long firstMember, long secondMember) {
        tx = new TransactionTemplate(transactionManager);
        String code = String.format("W%06d", ++sequence);
        return tx.execute(status -> {
            League league = League.create(LeagueName.of("리그" + code), InviteCode.of(code),
                    NOW.minusDays(1));
            entityManager.persist(league);
            entityManager.flush();
            long id = league.getId();
            Player first = enterListed(id, firstMember);
            Player second = enterListed(id, secondMember);
            Player lister = enterListed(id, 9_000_000L + sequence);
            league.start(List.of(first, second, lister), NOW);
            Stock stock = Stock.list(id, lister.getId(), StockName.of("종목"),
                    StockDescription.of("소개"));
            stock.open();
            entityManager.persist(stock);
            entityManager.flush();
            entityManager.persist(StockDailyPrice.open(id, stock.getId(), 1, 10_000));
            for (Player player : List.of(first, second, lister)) {
                entityManager.persist(Account.open(id, player.getId(), 1_000_000));
                entityManager.persist(Holding.allocate(id, player.getId(), stock.getId(),
                        player == lister ? 3_000 : 100));
            }
            return new Fixture(id, stock.getId(), first.getId(), second.getId(), lister.getId());
        });
    }

    private Player enterListed(long leagueId, long memberId) {
        Player player = Player.enter(leagueId, memberId, NOW.minusDays(1));
        player.completeListing(NOW.minusDays(1));
        entityManager.persist(player);
        entityManager.flush();
        return player;
    }

    private long placeBuy(Fixture fixture, long playerId, long quantity) {
        return limitOrderService.placeLimitOrder(new PlaceLimitOrder(fixture.leagueId, playerId,
                fixture.stockId, OrderSide.BUY, 9_000, quantity)).orderId();
    }

    private long placeSell(Fixture fixture, long playerId, long quantity) {
        return limitOrderService.placeLimitOrder(new PlaceLimitOrder(fixture.leagueId, playerId,
                fixture.stockId, OrderSide.SELL, 9_000, quantity)).orderId();
    }

    /** 요청을 동시에 실행하고 결과를 돌려준다. 예외는 던지지 않고 결과 자리에 담는다. */
    private List<Object> runConcurrently(List<? extends Callable<?>> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        final List<Future<Object>> futures = requests.stream()
                .map(request -> executor.submit(() -> {
                    start.await();
                    try {
                        return (Object) request.call();
                    } catch (Throwable e) {
                        return e;
                    }
                }))
                .toList();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).as("교착 없이 종료").isTrue();
        return futures.stream().map(future -> {
            try {
                return future.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Order order(long orderId) {
        return tx.execute(status -> entityManager.find(Order.class, orderId));
    }

    private long openBuyReservation(Fixture fixture, long playerId) {
        return tx.execute(status -> entityManager.createQuery(
                        "select o from Order o where o.playerId = :p", Order.class)
                .setParameter("p", playerId).getResultList().stream()
                .filter(o -> o.getSide() == OrderSide.BUY
                        && OrderStatus.openStatuses().contains(o.getStatus()))
                .mapToLong(o -> o.getReservedPrice() * o.pendingQuantity())
                .sum());
    }

    private Account account(Fixture fixture, long playerId) {
        return tx.execute(status -> entityManager.createQuery(
                        "select a from Account a where a.leagueId = :l and a.playerId = :p",
                        Account.class)
                .setParameter("l", fixture.leagueId).setParameter("p", playerId)
                .getSingleResult());
    }

    private Holding holding(Fixture fixture, long playerId) {
        return tx.execute(status -> entityManager.createQuery(
                        "select h from Holding h where h.leagueId = :l and h.playerId = :p",
                        Holding.class)
                .setParameter("l", fixture.leagueId).setParameter("p", playerId)
                .getSingleResult());
    }
}
