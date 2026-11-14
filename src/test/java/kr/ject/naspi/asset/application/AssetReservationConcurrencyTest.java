package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 동시에 들어온 주문이 계좌·보유 주식 락으로 차례로 처리되는지 검증한다. 각 스레드가 자기 트랜잭션을 커밋해야
 * 하므로 테스트 트랜잭션을 쓰지 않는다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import({AssetReservationService.class, StockQueryService.class, LocalStockSnapshotCache.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AssetReservationConcurrencyTest {

    private static final long LEAGUE_ID = 1L;
    private static final long PLAYER_ID = 10L;
    private static final int REQUESTS = 10;

    @Autowired
    private AssetReservationService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Account").executeUpdate();
            entityManager.createQuery("delete from Holding").executeUpdate();
            entityManager.createQuery("delete from Stock").executeUpdate();
        });
    }

    @Test
    @DisplayName("같은 참여자의 매수 주문이 동시에 들어와도 주문 가능 현금을 넘어 예약되지 않는다")
    void concurrentBuyReservations() throws Exception {
        // 100만 원으로 30만 원 주문 10건 → 3건만 예약되고 나머지는 거절
        final long accountId = persist(Account.open(LEAGUE_ID, PLAYER_ID, 1_000_000));

        long accepted = runConcurrently(
                () -> service.reserveCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 30));

        assertThat(accepted).isEqualTo(3);
        assertThat(find(Account.class, accountId).getReservedDeposit()).isEqualTo(900_000);
    }

    @Test
    @DisplayName("자사주 매도 주문이 동시에 들어와도 최소 유지분 1,000주를 남긴다")
    void concurrentTreasuryStockSellReservations() throws Exception {
        // 자사주 2,500주 중 매도 가능 1,500주로 200주 주문 10건 → 7건만 예약되고 나머지는 거절
        Stock stock = Stock.list(LEAGUE_ID, PLAYER_ID, StockName.of("가나다"),
                StockDescription.of("소개"));
        long stockId = persist(stock);
        final long holdingId = persist(Holding.allocate(LEAGUE_ID, PLAYER_ID, stockId, 2_500));

        long accepted = runConcurrently(
                () -> service.reserveStockForSell(LEAGUE_ID, PLAYER_ID, stockId, 200));

        assertThat(accepted).isEqualTo(7);
        assertThat(find(Holding.class, holdingId).getReservedQuantity()).isEqualTo(1_400);
    }

    /**
     * 같은 요청을 동시에 {@value #REQUESTS}건 실행하고 거절되지 않은 건수를 센다.
     */
    private long runConcurrently(Runnable request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            results.add(executor.submit(() -> {
                start.await();
                try {
                    request.run();
                    return true;
                } catch (NaspiBizException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        long accepted = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                accepted++;
            }
        }
        return accepted;
    }

    private long persist(Object entity) {
        return tx.execute(status -> {
            entityManager.persist(entity);
            entityManager.flush();
            return (Long) entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                    .getIdentifier(entity);
        });
    }

    private <T> T find(Class<T> type, long id) {
        return tx.execute(status -> entityManager.find(type, id));
    }
}
