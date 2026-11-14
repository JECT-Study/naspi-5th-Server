package kr.ject.naspi.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.AfterEach;
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
 * 동시에 들어온 매수 주문이 계좌 락으로 차례로 처리되는지 검증한다. 각 스레드가 자기 트랜잭션을 커밋해야 하므로
 * 테스트 트랜잭션을 쓰지 않는다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(AssetReservationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AssetReservationConcurrencyTest {

    private static final long LEAGUE_ID = 1L;
    private static final long PLAYER_ID = 10L;

    @Autowired
    private AssetReservationService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createQuery("delete from Account").executeUpdate());
    }

    @Test
    @DisplayName("같은 참여자의 매수 주문이 동시에 들어와도 주문 가능 현금을 넘어 예약되지 않는다")
    void concurrentReservations() throws Exception {
        // 100만 원으로 30만 원 주문 10건 → 3건만 예약되고 나머지는 거절
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        final long accountId = tx.execute(status -> {
            Account account = Account.open(LEAGUE_ID, PLAYER_ID, 1_000_000);
            entityManager.persist(account);
            return account.getId();
        });

        int requests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        Callable<Boolean> order = () -> {
            start.await();
            try {
                service.reserveCashForBuy(LEAGUE_ID, PLAYER_ID, 10_000, 30);
                return true;
            } catch (NaspiBizException e) {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_DEPOSIT);
                return false;
            }
        };
        for (int i = 0; i < requests; i++) {
            results.add(executor.submit(order));
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
        Account account = tx.execute(status -> entityManager.find(Account.class, accountId));
        assertThat(accepted).isEqualTo(3);
        assertThat(account.getReservedDeposit()).isEqualTo(900_000);
    }
}
