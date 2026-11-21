package kr.ject.naspi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.application.AssetWithdrawalService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.Player;
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
    private static final long LISTER = 9_001L;
    private static final long SELLER = 9_020L;
    private static final long BUYER = 9_030L;

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
    private AssetWithdrawalService withdrawalService;

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

    // ---- 회원 탈퇴 주문 취소 (ORD-11) ----

    /** 시작된 리그의 탈퇴 회원 플레이어·다른 플레이어·종목. 플레이어마다 현금 100만원, 탈퇴자 보유 100주. */
    private record Started(long leagueId, long stockId, long withdrawer, long other, long lister) {
    }

    private static final long MEMBER = 500L;

    private Started startedLeague(String code, LocalDateTime startedAt) {
        League league = entityManager.persistAndFlush(League.create(
                LeagueName.of("리그" + code), InviteCode.of(code), startedAt.minusDays(1)));
        long id = league.getId();
        List<Player> players = LongStream.rangeClosed(0, League.MIN_PARTICIPANTS_TO_START - 1)
                .mapToObj(i -> {
                    Player player = Player.enter(id, i == 0 ? MEMBER : 600L + i,
                            startedAt.minusDays(1));
                    player.completeListing(startedAt.minusDays(1));
                    return entityManager.persist(player);
                })
                .toList();
        entityManager.flush();
        league.start(players, startedAt);
        entityManager.flush();
        long withdrawer = players.get(0).getId();
        long other = players.get(1).getId();
        long lister = players.get(2).getId();
        Stock stock = Stock.list(id, lister, StockName.of("종목"), StockDescription.of("소개"));
        stock.open();
        long stockId = entityManager.persistAndGetId(stock, Long.class);
        for (long player : new long[] {withdrawer, other, lister}) {
            entityManager.persist(Account.open(id, player, 1_000_000));
            entityManager.persist(Holding.allocate(id, player, stockId,
                    player == lister ? 3_000 : 100));
        }
        entityManager.flush();
        return new Started(id, stockId, withdrawer, other, lister);
    }

    private Order startedBuy(Started started, long playerId, long quantity) {
        Order order = Order.limit(started.leagueId(), playerId, started.stockId(), OrderSide.BUY,
                9_000, quantity, 1, ORDERED_AT);
        reservationService.reserveCashForBuy(started.leagueId(), playerId, 9_000, quantity);
        order.recordReservedPrice(9_000);
        return entityManager.persistAndFlush(order);
    }

    private Order startedSell(Started started, long playerId, long quantity) {
        Order order = Order.limit(started.leagueId(), playerId, started.stockId(), OrderSide.SELL,
                9_000, quantity, 1, ORDERED_AT);
        reservationService.reserveStockForSell(started.leagueId(), playerId, started.stockId(),
                quantity);
        return entityManager.persistAndFlush(order);
    }

    private Order partiallyFilledBuy(Started started, long playerId, long quantity, long filled) {
        Order order = startedBuy(started, playerId, quantity);
        order.fill(filled);
        reservationService.releaseCashForBuy(started.leagueId(), playerId, 9_000, filled);
        return order;
    }

    private Account startedAccount(Started started, long playerId) {
        return entityManager.getEntityManager().createQuery(
                        "select a from Account a where a.leagueId = :l and a.playerId = :p",
                        Account.class)
                .setParameter("l", started.leagueId()).setParameter("p", playerId)
                .getSingleResult();
    }

    private Holding startedHolding(Started started, long playerId) {
        return entityManager.getEntityManager().createQuery(
                        "select h from Holding h where h.leagueId = :l and h.playerId = :p",
                        Holding.class)
                .setParameter("l", started.leagueId()).setParameter("p", playerId)
                .getSingleResult();
    }

    @Test
    @DisplayName("회원이 탈퇴하면 참여 중인 모든 리그의 미체결 주문(대기·부분 체결)이 취소되고 사유는 회원 탈퇴로 기록된다")
    void cancelsAllOpenOrdersOfMember() {
        Started a = startedLeague("BBBBBBB", ORDERED_AT);
        Started b = startedLeague("CCCCCCC", ORDERED_AT);
        List<Order> orders = List.of(
                startedBuy(a, a.withdrawer(), 10), partiallyFilledBuy(a, a.withdrawer(), 10, 3),
                startedBuy(b, b.withdrawer(), 10), partiallyFilledBuy(b, b.withdrawer(), 10, 3));

        int count = service.cancelAllOfMember(MEMBER);

        assertThat(count).isEqualTo(4);
        for (Order order : orders) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(order.getCancelReason()).isEqualTo(CancelReason.MEMBER_WITHDRAWN);
            assertThat(order.getCanceledAt()).isEqualTo(ORDERED_AT);
        }
    }

    @Test
    @DisplayName("탈퇴 취소로 미체결 잔여 수량에 예약된 현금과 주식이 해제되어 예약이 0이 된다")
    void withdrawalReleasesReservation() {
        Started a = startedLeague("BBBBBBB", ORDERED_AT);
        startedBuy(a, a.withdrawer(), 10);
        startedSell(a, a.withdrawer(), 10);
        assertThat(startedAccount(a, a.withdrawer()).getReservedDeposit()).isEqualTo(90_000);
        assertThat(startedHolding(a, a.withdrawer()).getReservedQuantity()).isEqualTo(10);

        service.cancelAllOfMember(MEMBER);

        assertThat(startedAccount(a, a.withdrawer()).getReservedDeposit()).isZero();
        assertThat(startedHolding(a, a.withdrawer()).getReservedQuantity()).isZero();
        assertThat(startedHolding(a, a.withdrawer()).getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("탈퇴 주문 취소 뒤에는 예약이 남지 않아 현금 폐기가 거절되지 않는다")
    void cashDiscardSucceedsAfterWithdrawalCancel() {
        Started a = startedLeague("BBBBBBB", ORDERED_AT);
        startedBuy(a, a.withdrawer(), 10);
        startedSell(a, a.withdrawer(), 10);
        // 대조: 취소 없이 폐기하면 예약이 남아 거절된다 (그래서 순서는 취소 → 폐기)
        assertThatThrownBy(() -> withdrawalService.discardCashOfMember(MEMBER))
                .isInstanceOf(NaspiRuntimeException.class);

        service.cancelAllOfMember(MEMBER);
        withdrawalService.discardCashOfMember(MEMBER);

        assertThat(startedAccount(a, a.withdrawer()).getDeposit()).isZero();
        assertThat(startedAccount(a, a.withdrawer()).getReservedDeposit()).isZero();
        assertThat(startedHolding(a, a.withdrawer()).getQuantity()).isEqualTo(100);
        assertThat(startedHolding(a, a.withdrawer()).getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("부분 체결된 주문을 탈퇴로 취소하면 체결된 부분은 유지되고 잔여 수량만 취소 수량이 된다")
    void withdrawalKeepsFilledPart() {
        Started a = startedLeague("BBBBBBB", ORDERED_AT);
        Order order = partiallyFilledBuy(a, a.withdrawer(), 10, 3);

        service.cancelAllOfMember(MEMBER);

        assertThat(order.filledQuantity()).isEqualTo(3);
        assertThat(order.canceledQuantity()).isEqualTo(7);
        assertThat(startedAccount(a, a.withdrawer()).getReservedDeposit()).isZero();
    }

    @Test
    @DisplayName("탈퇴 회원의 종료된 주문과 다른 회원의 주문은 취소하지 않는다")
    void withdrawalSkipsOthers() {
        Started a = startedLeague("BBBBBBB", ORDERED_AT);
        Order filled = startedBuy(a, a.withdrawer(), 10);
        filled.fill(10);
        reservationService.releaseCashForBuy(a.leagueId(), a.withdrawer(), 9_000, 10);
        Order userCanceled = startedBuy(a, a.withdrawer(), 10);
        userCanceled.cancel(CancelReason.USER_REQUESTED, ORDERED_AT);
        reservationService.releaseCashForBuy(a.leagueId(), a.withdrawer(), 9_000, 10);
        Order expired = startedBuy(a, a.withdrawer(), 10);
        expired.expire(EXPIRED_AT);
        reservationService.releaseCashForBuy(a.leagueId(), a.withdrawer(), 9_000, 10);
        Order others = startedBuy(a, a.other(), 10);

        int count = service.cancelAllOfMember(MEMBER);

        assertThat(count).isZero();
        assertThat(filled.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(userCanceled.getCancelReason()).isEqualTo(CancelReason.USER_REQUESTED);
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(others.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(startedAccount(a, a.other()).getReservedDeposit()).isEqualTo(90_000);
    }

    @Test
    @DisplayName("종료 시각이 지난 리그의 미체결 주문도 거래 가능 확인 없이 탈퇴로 취소된다")
    void withdrawalCancelsInLeaguePastEndsAt() {
        Started a = startedLeague("BBBBBBB", ORDERED_AT.minusDays(10));
        Order order = startedBuy(a, a.withdrawer(), 10);

        int count = service.cancelAllOfMember(MEMBER);

        assertThat(count).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(CancelReason.MEMBER_WITHDRAWN);
        assertThat(startedAccount(a, a.withdrawer()).getReservedDeposit()).isZero();
    }

    @Test
    @DisplayName("진행 중인 리그나 미체결 주문이 없으면 아무것도 취소하지 않는다")
    void withdrawalWithoutOrders() {
        // 대기실만 있는 회원
        entityManager.persistAndFlush(Player.enter(leagueId, 700L, ORDERED_AT));
        assertThat(service.cancelAllOfMember(700L)).isZero();

        // 진행 중 리그에 참여했지만 미체결 주문이 없는 회원
        startedLeague("BBBBBBB", ORDERED_AT);
        assertThat(service.cancelAllOfMember(MEMBER)).isZero();
    }

    @Test
    @DisplayName("탈퇴 주문 취소는 탈퇴 트랜잭션 안에서만 실행된다")
    void withdrawalCancelRequiresTransaction() {
        TransactionTemplate noTransaction = new TransactionTemplate(transactionManager);
        noTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

        assertThatThrownBy(() -> noTransaction.executeWithoutResult(
                status -> service.cancelAllOfMember(MEMBER)))
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
