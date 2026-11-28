package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.league.domain.TradingDayRepository;
import kr.ject.naspi.market.application.FinalEvaluationPriceUseCase;
import kr.ject.naspi.market.application.StockTradingDayService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.application.OrderClosingTestConfig;
import kr.ject.naspi.order.application.TradeQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/** 여러 서버가 리그 종료·거래일 전환을 동시에 실행해도 리그 락으로 직렬화되어 한 번만 처리되는지 검증한다 (LEA-12). */
@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueEndUseCase.class, TradingDayChangeUseCase.class, StockTradingDayService.class,
        OrderClosingTestConfig.class, MutableClock.Config.class, LeagueResultUseCase.class,
        FinalEvaluationPriceUseCase.class, TradeQueryService.class, AssetQueryService.class,
        DailyAssetSnapshotUseCase.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LeagueEndConcurrencyTest {

    /** 2026-11-15 10:00 시작: 6일차는 11-21 09:00에 끝나고, 종료 시각은 11-22 09:00. */
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 11, 22, 9, 0);
    private static final int THREADS = 4;

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private LeagueEndUseCase leagueEndUseCase;

    @Autowired
    private TradingDayChangeUseCase tradingDayChangeUseCase;

    @Autowired
    private TradingDayRepository tradingDayRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void stubNames() {
        given(memberService.getNames(any())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(), id -> "회원" + id));
        });
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from DailyAssetSnapshot").executeUpdate();
            entityManager.createQuery("delete from LeagueResultHolding").executeUpdate();
            entityManager.createQuery("delete from LeagueResultPlayer").executeUpdate();
            entityManager.createQuery("delete from LeagueResultStock").executeUpdate();
            entityManager.createQuery("delete from LeagueResult").executeUpdate();
            entityManager.createQuery("delete from Holding").executeUpdate();
            entityManager.createQuery("delete from Account").executeUpdate();
            entityManager.createQuery("delete from StockDailyPrice").executeUpdate();
            entityManager.createQuery("delete from Stock").executeUpdate();
            entityManager.createQuery("delete from TradingDay").executeUpdate();
            entityManager.createQuery("delete from Player").executeUpdate();
            entityManager.createQuery("delete from League").executeUpdate();
        });
    }

    @Test
    @DisplayName("여러 서버가 동시에 리그 종료를 실행해도 한 번만 처리된다")
    void onlyOnceUnderConcurrentRuns() throws Exception {
        long leagueId = startedLeagueWithStock();

        List<Callable<Boolean>> tasks = IntStream.range(0, THREADS)
                .mapToObj(i -> (Callable<Boolean>) () -> leagueEndUseCase.end(leagueId, ENDS_AT))
                .toList();
        List<Boolean> results = runConcurrently(tasks);

        assertThat(results).containsOnlyOnce(true);
        assertEndedOnce(leagueId);
    }

    @Test
    @DisplayName("같은 09:00에 거래일 전환과 리그 종료가 동시에 실행되어도 6일차·7일차 마감이 한 번씩만 처리된다")
    void withTradingDayChange() throws Exception {
        long leagueId = startedLeagueWithStock();
        // 5일차까지 마감된 상태 (6일차 진행 중)
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                tradingDayChangeUseCase.change(leagueId, ENDS_AT.minusDays(2)));

        List<Callable<Boolean>> tasks = new ArrayList<>();
        IntStream.range(0, THREADS).forEach(i -> tasks.add(i % 2 == 0
                ? () -> leagueEndUseCase.end(leagueId, ENDS_AT)
                : () -> tradingDayChangeUseCase.change(leagueId, ENDS_AT) > 0));
        runConcurrently(tasks);

        assertEndedOnce(leagueId);
    }

    private List<Boolean> runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }

    /** 리그가 종료되었고, 1~7일차가 모두 한 번씩 마감되었으며 시세 행이 일차마다 하나씩만 있고 결과가 하나다. */
    private void assertEndedOnce(long leagueId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            assertThat(entityManager.find(League.class, leagueId).getStatus())
                    .isEqualTo(LeagueStatus.ENDED);
            assertThat(tradingDayRepository.findAllByLeagueIdOrderByDayNumber(leagueId).stream()
                    .filter(TradingDay::isClosed).map(TradingDay::getDayNumber).toList())
                    .containsExactly(1, 2, 3, 4, 5, 6, 7);
            List<StockDailyPrice> prices = entityManager.createQuery(
                            "select d from StockDailyPrice d where d.leagueId = :l "
                                    + "order by d.tradingDay", StockDailyPrice.class)
                    .setParameter("l", leagueId).getResultList();
            assertThat(prices).extracting(StockDailyPrice::getTradingDay)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7);
            assertThat(prices).allSatisfy(price -> assertThat(price.getClosePrice()).isNotNull());
            assertThat(entityManager.createQuery(
                            "select count(r) from LeagueResult r where r.leagueId = :l", Long.class)
                    .setParameter("l", leagueId).getSingleResult()).isEqualTo(1L);
        });
    }

    private long startedLeagueWithStock() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            League league = League.create(LeagueName.of("리그"), InviteCode.of("EEEEEEE"),
                    STARTED_AT.minusDays(1));
            entityManager.persist(league);
            entityManager.flush();
            List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                    .mapToObj(memberId -> {
                        Player player = Player.enter(league.getId(), memberId,
                                STARTED_AT.minusDays(1));
                        player.completeListing(STARTED_AT.minusDays(1));
                        entityManager.persist(player);
                        entityManager.flush();
                        entityManager.persist(
                                Account.open(league.getId(), player.getId(), 100_000_000));
                        return player;
                    })
                    .toList();
            league.start(players, STARTED_AT);
            tradingDayRepository.saveAll(TradingDay.of(league.getId(), league.tradingSchedule()));
            Stock stock = Stock.list(league.getId(), players.get(0).getId(),
                    StockName.of("가나다"), StockDescription.of("소개"));
            stock.open();
            entityManager.persist(stock);
            entityManager.flush();
            entityManager.persist(StockDailyPrice.open(league.getId(), stock.getId(), 1, 10_000));
            return league.getId();
        });
    }
}
