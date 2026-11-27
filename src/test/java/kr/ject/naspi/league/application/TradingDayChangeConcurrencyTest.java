package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.league.domain.TradingDayRepository;
import kr.ject.naspi.market.application.StockTradingDayService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.application.OrderClosingTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 여러 서버가 같은 전환을 동시에 실행해도 리그 락으로 직렬화되어 한 번만 처리되는지 검증한다 (LEA-11). */
@ActiveProfiles("test")
@DataJpaTest
@Import({TradingDayChangeUseCase.class, StockTradingDayService.class,
        OrderClosingTestConfig.class, MutableClock.Config.class, DailyAssetSnapshotUseCase.class,
        AssetQueryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TradingDayChangeConcurrencyTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 16, 9, 0);
    private static final int THREADS = 4;

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private TradingDayChangeUseCase useCase;

    @Autowired
    private TradingDayRepository tradingDayRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from DailyAssetSnapshot").executeUpdate();
            entityManager.createQuery("delete from StockDailyPrice").executeUpdate();
            entityManager.createQuery("delete from Stock").executeUpdate();
            entityManager.createQuery("delete from Account").executeUpdate();
            entityManager.createQuery("delete from TradingDay").executeUpdate();
            entityManager.createQuery("delete from League").executeUpdate();
        });
    }

    @Test
    @DisplayName("여러 서버가 동시에 전환을 실행해도 한 번만 처리된다")
    void onlyOnceUnderConcurrentRuns() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long leagueId = tx.execute(status -> startedLeague("EEEEEEE"));

        assertThat(runConcurrently(leagueId)).isEqualTo(1);
        List<Integer> closed = tx.execute(status ->
                tradingDayRepository.findAllByLeagueIdOrderByDayNumber(leagueId).stream()
                        .filter(TradingDay::isClosed).map(TradingDay::getDayNumber).toList());
        assertThat(closed).containsExactly(1);
    }

    @Test
    @DisplayName("여러 서버가 동시에 전환을 실행해도 거래일별 자산 집계는 한 번만 저장된다")
    void concurrentChangeRecordsOnce() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long leagueId = tx.execute(status -> {
            long id = startedLeague("FFFFFFF");
            Stock stock = Stock.list(id, 10L, StockName.of("가나다"), StockDescription.of("소개"));
            stock.open();
            entityManager.persist(stock);
            entityManager.flush();
            entityManager.persist(StockDailyPrice.open(id, stock.getId(), 1, 10_000));
            entityManager.persist(Account.open(id, 10L, 100_000_000));
            entityManager.persist(Account.open(id, 20L, 100_000_000));
            return id;
        });

        assertThat(runConcurrently(leagueId)).isEqualTo(1);
        Long rows = tx.execute(status -> entityManager.createQuery(
                        "select count(d) from DailyAssetSnapshot d where d.leagueId = :l "
                                + "and d.tradingDay = 1", Long.class)
                .setParameter("l", leagueId).getSingleResult());
        assertThat(rows).isEqualTo(2);
    }

    private long startedLeague(String code) {
        League league = League.create(LeagueName.of("리그"), InviteCode.of(code),
                STARTED_AT.minusDays(1));
        entityManager.persist(league);
        entityManager.flush();
        List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(league.getId(), memberId,
                            STARTED_AT.minusDays(1));
                    player.completeListing(STARTED_AT.minusDays(1));
                    return player;
                })
                .toList();
        league.start(players, STARTED_AT);
        tradingDayRepository.saveAll(TradingDay.of(league.getId(), league.tradingSchedule()));
        return league.getId();
    }

    private int runConcurrently(long leagueId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        IntStream.range(0, THREADS).forEach(i -> futures.add(executor.submit(
                (Callable<Integer>) () -> {
                    ready.countDown();
                    go.await();
                    return useCase.change(leagueId, DAY2);
                })));
        ready.await();
        go.countDown();
        int total = 0;
        for (Future<Integer> future : futures) {
            total += future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        return total;
    }
}
