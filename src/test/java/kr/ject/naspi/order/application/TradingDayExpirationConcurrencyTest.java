package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.application.AssetSettlementService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.DailyAssetSnapshotUseCase;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueTradingService;
import kr.ject.naspi.league.application.TradingDayChangeUseCase;
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
 * 거래일 전환의 미체결 만료(ORD-09)와 사용자 취소(ORD-07)가 동시에 처리되어도 주문이 한 번만 종료되고 예약이
 * 한 번만 해제되는지 검증한다. 둘 다 같은 리그 락 안에서 실행되므로 차례로 처리된다. 각 스레드가 자기 트랜잭션을
 * 커밋해야 하므로 테스트 트랜잭션을 쓰지 않는다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import({LimitOrderService.class, OrderExecutionSupport.class, OpenOrderClosingService.class,
        TradingDayChangeUseCase.class, LeagueTradingService.class, LeagueQueryService.class,
        StockQueryService.class, StockExecutionService.class, StockTradingDayService.class,
        LocalStockSnapshotCache.class, AssetReservationService.class,
        AssetSettlementService.class, DailyAssetSnapshotUseCase.class, AssetQueryService.class,
        TradingDayExpirationConcurrencyTest.ClockConfigForTest.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TradingDayExpirationConcurrencyTest {

    private static final LocalDateTime DAY1 = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 16, 9, 0);
    private static final int ROUNDS = 10;

    @TestConfiguration
    static class ClockConfigForTest {

        @Bean
        SettableClock clock() {
            return new SettableClock();
        }
    }

    /** 테스트가 현재 시각을 옮길 수 있는 시계. 스레드 간에 보이도록 volatile로 둔다. */
    static class SettableClock extends Clock {

        private volatile Instant instant = DAY1.atZone(ClockConfig.KST).toInstant();

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

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private LimitOrderService limitOrderService;

    @Autowired
    private TradingDayChangeUseCase changeUseCase;

    @Autowired
    private SettableClock clock;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private int sequence;

    @AfterEach
    void tearDown() {
        clock.set(DAY1);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from orders").executeUpdate();
            entityManager.createQuery("delete from DailyAssetSnapshot").executeUpdate();
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
    @DisplayName("전환 직후 취소와 거래일 만료가 동시에 처리되어도 주문은 한 번만 종료되고 예약은 한 번만 해제된다")
    void cancelAndExpireRaceReleasesOnce() throws Exception {
        int canceledFirst = 0;
        for (int round = 0; round < ROUNDS; round++) {
            Fixture fixture = newLeague();
            long orderId = place(fixture);
            clock.set(DAY2.plusSeconds(1));

            List<Boolean> results = runConcurrently(List.of(
                    () -> limitOrderService.cancelOrder(fixture.leagueId, fixture.buyer, orderId),
                    () -> changeUseCase.change(fixture.leagueId, DAY2.plusSeconds(1))));

            assertThat(results.get(1)).as("전환은 항상 성공한다").isTrue();
            Order order = order(orderId);
            if (results.get(0)) {
                // 취소가 먼저 확정되었고 전환은 이미 취소된 주문을 읽지 않는다
                canceledFirst++;
                assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
                assertThat(order.getCancelReason()).isEqualTo(CancelReason.USER_REQUESTED);
            } else {
                // 만료가 먼저 확정되었고 취소는 상태 오류로 거절된다
                assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
                assertThat(order.getCancelReason()).isEqualTo(CancelReason.TRADING_DAY_EXPIRED);
                assertThat(order.getCanceledAt()).isEqualTo(DAY2);
            }
            assertThat(account(fixture, fixture.buyer).getReservedDeposit())
                    .as("예약 현금은 한 번만 해제되어 0이다 (취소 선행 %d회째)", canceledFirst).isZero();
            assertThat(account(fixture, fixture.buyer).getDeposit()).isEqualTo(1_000_000);
            clock.set(DAY1);
        }
    }

    @Test
    @DisplayName("만료된 주문은 취소할 수 없고 예약이 두 번 해제되지 않는다")
    void cancelAfterExpireRejected() {
        Fixture fixture = newLeague();
        long orderId = place(fixture);
        clock.set(DAY2.plusSeconds(1));
        changeUseCase.change(fixture.leagueId, DAY2.plusSeconds(1));

        assertThatThrownBy(() -> limitOrderService.cancelOrder(
                fixture.leagueId, fixture.buyer, orderId))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);

        assertThat(order(orderId).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(account(fixture, fixture.buyer).getReservedDeposit()).isZero();
        assertThat(account(fixture, fixture.buyer).getDeposit()).isEqualTo(1_000_000);
    }

    private record Fixture(long leagueId, long stockId, long lister, long buyer) {
    }

    /** 리그를 시작하고 종목·자산을 준비한다. 전환은 리그마다 한 번뿐이라 라운드마다 새 리그를 쓴다. */
    private Fixture newLeague() {
        tx = new TransactionTemplate(transactionManager);
        String code = String.format("R%06d", ++sequence);
        // 종목은 상장자(플레이어)마다 하나이므로 리그마다 다른 플레이어 ID를 쓴다
        long lister = sequence * 100L + 1;
        long buyer = sequence * 100L + 30;
        return tx.execute(status -> {
            League league = League.create(LeagueName.of("리그"), InviteCode.of(code),
                    DAY1.minusDays(1));
            entityManager.persist(league);
            entityManager.flush();
            List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                    .mapToObj(memberId -> {
                        Player player = Player.enter(league.getId(), memberId, DAY1.minusDays(1));
                        player.completeListing(DAY1.minusDays(1));
                        return player;
                    })
                    .toList();
            league.start(players, DAY1);
            long leagueId = league.getId();
            TradingDay.of(leagueId, league.tradingSchedule()).forEach(entityManager::persist);

            Stock stock = Stock.list(leagueId, lister, StockName.of("종목"),
                    StockDescription.of("소개"));
            stock.open();
            entityManager.persist(stock);
            entityManager.flush();
            entityManager.persist(StockDailyPrice.open(leagueId, stock.getId(), 1, 10_000));
            for (long player : new long[] {lister, buyer}) {
                entityManager.persist(Account.open(leagueId, player, 1_000_000));
                entityManager.persist(Holding.allocate(leagueId, player, stock.getId(),
                        player == lister ? 3_000 : 0));
            }
            return new Fixture(leagueId, stock.getId(), lister, buyer);
        });
    }

    private long place(Fixture fixture) {
        return limitOrderService.placeLimitOrder(new PlaceLimitOrder(fixture.leagueId,
                fixture.buyer, fixture.stockId, OrderSide.BUY, 9_000, 10)).orderId();
    }

    /** 요청을 동시에 실행하고 요청마다 상태 오류 없이 끝났는지를 돌려준다. 다른 예외는 테스트를 실패시킨다. */
    private List<Boolean> runConcurrently(List<? extends Callable<?>> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        final List<Future<Boolean>> futures = requests.stream()
                .map(request -> executor.submit(() -> {
                    start.await();
                    try {
                        request.call();
                        return true;
                    } catch (NaspiBizException e) {
                        assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
                        return false;
                    }
                }))
                .toList();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        return futures.stream().map(future -> {
            try {
                return future.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    private Order order(long orderId) {
        return tx.execute(status -> entityManager.find(Order.class, orderId));
    }

    private Account account(Fixture fixture, long playerId) {
        return tx.execute(status -> entityManager.createQuery(
                        "select a from Account a where a.leagueId = :l and a.playerId = :p",
                        Account.class)
                .setParameter("l", fixture.leagueId).setParameter("p", playerId)
                .getSingleResult());
    }
}
