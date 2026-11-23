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
 * 리그 종료의 미체결 주문 취소(ORD-10)가 회원 탈퇴 취소·사용자 취소·밀린 거래일 만료와 동시에 처리되어도
 * 예약이 한 번만 해제되는지 검증한다. 각 스레드가 자기 트랜잭션을 커밋해야 하므로 테스트 트랜잭션을 쓰지 않는다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import({LimitOrderService.class, OrderExecutionSupport.class, OpenOrderClosingService.class,
        LeagueTradingService.class, LeagueQueryService.class, StockQueryService.class,
        StockExecutionService.class, LocalStockSnapshotCache.class, AssetReservationService.class,
        AssetSettlementService.class, AssetWithdrawalService.class,
        LeagueEndOrderConcurrencyTest.FixedClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LeagueEndOrderConcurrencyTest {

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
    private LeagueTradingService leagueTradingService;

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
    @DisplayName("리그 종료 취소와 회원 탈퇴 취소가 동시에 처리되어도 주문은 한 번만 취소되고 예약은 한 번만 해제된다")
    void leagueEndAndWithdrawalCancelReleaseOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long member = memberId(round);
            Fixture fixture = newLeague(member, member + 1);
            long orderId = placeBuy(fixture, fixture.first, 10);

            List<Object> results = runConcurrently(List.of(
                    () -> endLeague(fixture.leagueId), () -> withdraw(member)));

            for (Object result : results) {
                assertThat(result).isNotInstanceOf(Throwable.class);
            }
            Order order = order(orderId);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(order.getCancelReason())
                    .isIn(CancelReason.LEAGUE_ENDED, CancelReason.MEMBER_WITHDRAWN);
            assertThat((int) results.get(0) + (int) results.get(1)).isEqualTo(1);
            assertThat(account(fixture, fixture.first).getReservedDeposit()).isZero();
        }
    }

    @Test
    @DisplayName("종료 직전의 사용자 취소와 리그 종료 취소가 겹쳐도 예약이 이중 해제되지 않는다")
    void leagueEndAndUserCancelBeforeEndsAt() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long member = memberId(round);
            Fixture fixture = newLeague(member, member + 1);
            long orderId = placeBuy(fixture, fixture.first, 10);

            List<Object> results = runConcurrently(List.of(
                    () -> endLeague(fixture.leagueId),
                    () -> limitOrderService.cancelOrder(fixture.leagueId, fixture.first,
                            orderId)));

            Order order = order(orderId);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            if (results.get(0).equals(1)) {
                assertThat(results.get(1)).isInstanceOfSatisfying(NaspiBizException.class, e ->
                        assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION));
                assertThat(order.getCancelReason()).isEqualTo(CancelReason.LEAGUE_ENDED);
            } else {
                assertThat(results.get(0)).isEqualTo(0);
                assertThat(results.get(1)).isNotInstanceOf(Throwable.class);
                assertThat(order.getCancelReason()).isEqualTo(CancelReason.USER_REQUESTED);
            }
            assertThat(account(fixture, fixture.first).getReservedDeposit()).isZero();
        }
    }

    @Test
    @DisplayName("밀린 거래일 만료와 리그 종료 취소가 동시에 실행되어도 각 주문은 한 번만 종료되고 예약은 한 번만 해제된다")
    void leagueEndAndBacklogExpirationKeepConsistent() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long member = memberId(round);
            Fixture fixture = newLeague(member, member + 1);
            long orderId = placeBuy(fixture, fixture.first, 10);

            List<Object> results = runConcurrently(List.of(
                    () -> endLeague(fixture.leagueId), () -> expire(fixture.leagueId)));

            for (Object result : results) {
                assertThat(result).isNotInstanceOf(Throwable.class);
            }
            Order order = order(orderId);
            assertThat(order.getStatus()).isIn(OrderStatus.EXPIRED, OrderStatus.CANCELED);
            assertThat((int) results.get(0) + (int) results.get(1)).isEqualTo(1);
            assertThat(account(fixture, fixture.first).getReservedDeposit()).isZero();
        }
    }

    private long memberId(int round) {
        return 1_000L + round * 10L;
    }

    /** 리그 종료 트랜잭션을 흉내 낸다. 취소한 주문 수를 돌려준다. */
    private int endLeague(long leagueId) {
        return new TransactionTemplate(transactionManager).execute(
                status -> closingService.cancelAllOfLeague(leagueId, NOW));
    }

    /** 밀린 거래일 전환의 만료를 흉내 낸다 (리그 락을 잡은 뒤 1일차 주문 만료). */
    private int expire(long leagueId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            leagueTradingService.lockForClosing(leagueId);
            return closingService.expireTradingDay(leagueId, 1, NOW);
        });
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
