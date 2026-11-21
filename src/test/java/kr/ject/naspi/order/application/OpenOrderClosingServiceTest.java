package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 거래일 만료 일괄 처리(ORD-09)가 실제 asset 서비스와 함께 주문 종료·예약 해제를 하는지 확인한다. */
@ActiveProfiles("test")
@DataJpaTest
@Import({OrderClosingTestConfig.class, OpenOrderClosingServiceTest.FixedClockConfig.class})
class OpenOrderClosingServiceTest {

    private static final LocalDateTime ORDERED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2026, 11, 16, 9, 0);
    private static final long LISTER = 1L;
    private static final long SELLER = 20L;
    private static final long BUYER = 30L;

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(ORDERED_AT.atZone(ClockConfig.KST).toInstant(), ClockConfig.KST);
        }
    }

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private OpenOrderClosingService service;

    @Autowired
    private AssetReservationService reservationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TestEntityManager entityManager;

    private long leagueId;
    private long stockId;

    @BeforeEach
    void setUp() {
        leagueId = entityManager.persistAndFlush(League.create(
                LeagueName.of("리그"), InviteCode.of("AAAAAAA"), ORDERED_AT.minusDays(1))).getId();
        Stock stock = Stock.list(leagueId, LISTER, StockName.of("종목"), StockDescription.of("소개"));
        stock.open();
        stockId = entityManager.persistAndGetId(stock, Long.class);
        for (long player : new long[] {LISTER, SELLER, BUYER}) {
            entityManager.persist(Account.open(leagueId, player, 1_000_000));
            entityManager.persist(Holding.allocate(leagueId, player, stockId,
                    player == LISTER ? 3_000 : player == SELLER ? 100 : 0));
        }
        entityManager.flush();
    }

    @Test
    @DisplayName("거래일이 바뀌면 지정가 미체결 주문이 만료되고 사유와 만료 일시(09:00:00)가 기록된다")
    void expiresPendingLimitOrders() {
        Order buy = reservedBuy(BUYER, 1, 10);
        Order sell = reservedSell(SELLER, 1, 10);

        int count = service.expireTradingDay(leagueId, 1, EXPIRED_AT);

        assertThat(count).isEqualTo(2);
        for (Order order : new Order[] {buy, sell}) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(order.getCancelReason()).isEqualTo(CancelReason.TRADING_DAY_EXPIRED);
            assertThat(order.getCanceledAt()).isEqualTo(EXPIRED_AT);
        }
    }

    @Test
    @DisplayName("만료된 주문의 미체결 잔여 수량에 예약된 현금과 주식이 해제된다")
    void releasesReservationOfRemaining() {
        reservedBuy(BUYER, 1, 10);
        reservedSell(SELLER, 1, 10);
        assertThat(account(BUYER).getReservedDeposit()).isEqualTo(90_000);
        assertThat(holding(SELLER).getReservedQuantity()).isEqualTo(10);

        service.expireTradingDay(leagueId, 1, EXPIRED_AT);

        assertThat(account(BUYER).getReservedDeposit()).isZero();
        assertThat(holding(SELLER).getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("부분 체결된 주문이 만료되면 이미 체결된 부분은 유지되고 잔여 수량만 만료·해제된다")
    void keepsFilledPart() {
        Order buy = reservedBuy(BUYER, 1, 30);
        // 10주 체결: 체결분의 예약은 정산에서 소진되므로 여기서는 같은 만큼 예약을 줄여 재현한다
        buy.fill(10);
        reservationService.releaseCashForBuy(leagueId, BUYER, 9_000, 10);

        service.expireTradingDay(leagueId, 1, EXPIRED_AT);

        assertThat(buy.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(buy.filledQuantity()).isEqualTo(10);
        assertThat(buy.canceledQuantity()).isEqualTo(20);
        assertThat(account(BUYER).getReservedDeposit()).isZero();
        assertThat(holding(BUYER).getQuantity()).isZero();
    }

    @Test
    @DisplayName("거래일 변경으로 만료된 미체결 잔여 수량은 취소 수량에 합산된다")
    void expiredCountsAsCanceledQuantity() {
        Order buy = reservedBuy(BUYER, 1, 10);

        service.expireTradingDay(leagueId, 1, EXPIRED_AT);

        assertThat(buy.canceledQuantity()).isEqualTo(10);
        assertThat(buy.pendingQuantity()).isZero();
    }

    @Test
    @DisplayName("체결 완료·취소된 주문과 다른 리그의 주문은 만료하지 않는다")
    void skipsClosedAndOtherLeagueOrders() {
        Order filled = reservedBuy(BUYER, 1, 10);
        filled.fill(10);
        reservationService.releaseCashForBuy(leagueId, BUYER, 9_000, 10);
        Order canceled = reservedBuy(BUYER, 1, 10);
        canceled.cancel(CancelReason.USER_REQUESTED, ORDERED_AT);
        reservationService.releaseCashForBuy(leagueId, BUYER, 9_000, 10);
        Order target = reservedBuy(BUYER, 1, 10);
        long otherLeagueId = leagueId + 1_000;
        Order other = entityManager.persistAndFlush(Order.limit(otherLeagueId, BUYER, stockId,
                OrderSide.BUY, 9_000, 10, 1, ORDERED_AT));

        int count = service.expireTradingDay(leagueId, 1, EXPIRED_AT);

        assertThat(count).isEqualTo(1);
        assertThat(target.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(filled.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.getCancelReason()).isEqualTo(CancelReason.USER_REQUESTED);
        assertThat(other.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("마감하는 거래일 이하의 주문만 만료한다")
    void expiresUpToClosingDay() {
        Order day1 = reservedBuy(BUYER, 1, 10);
        Order day2 = reservedBuy(BUYER, 2, 10);

        int count = service.expireTradingDay(leagueId, 1, EXPIRED_AT);

        assertThat(count).isEqualTo(1);
        assertThat(day1.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(day2.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("만료는 리그 락을 잡은 트랜잭션 안에서만 실행된다")
    void requiresTransaction() {
        // 테스트 트랜잭션을 잠시 멈추고 트랜잭션 없이 호출한다
        TransactionTemplate noTransaction = new TransactionTemplate(transactionManager);
        noTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

        assertThatThrownBy(() -> noTransaction.executeWithoutResult(
                status -> service.expireTradingDay(leagueId, 1, EXPIRED_AT)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Order reservedBuy(long playerId, int tradingDay, long quantity) {
        Order order = Order.limit(leagueId, playerId, stockId, OrderSide.BUY, 9_000, quantity,
                tradingDay, ORDERED_AT);
        reservationService.reserveCashForBuy(leagueId, playerId, 9_000, quantity);
        order.recordReservedPrice(9_000);
        return entityManager.persistAndFlush(order);
    }

    private Order reservedSell(long playerId, int tradingDay, long quantity) {
        Order order = Order.limit(leagueId, playerId, stockId, OrderSide.SELL, 9_000, quantity,
                tradingDay, ORDERED_AT);
        reservationService.reserveStockForSell(leagueId, playerId, stockId, quantity);
        return entityManager.persistAndFlush(order);
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
}
