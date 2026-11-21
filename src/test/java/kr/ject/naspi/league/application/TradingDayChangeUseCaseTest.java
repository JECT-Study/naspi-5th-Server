package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.league.domain.TradingDayRepository;
import kr.ject.naspi.market.application.StockExecutionService;
import kr.ject.naspi.market.application.StockTradingDayService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.application.OrderClosingTestConfig;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({TradingDayChangeUseCase.class, StockTradingDayService.class,
        OrderClosingTestConfig.class, MutableClock.Config.class})
class TradingDayChangeUseCaseTest {

    /** 시작 후 1일차: 2026-11-15 10:00 시작, 2일차는 11-16 09:00. */
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 16, 9, 0);

    private static final long LISTER = 10L;
    private static final long SELLER = 20L;
    private static final long BUYER = 30L;

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private TradingDayChangeUseCase useCase;

    @Autowired
    private AssetReservationService reservationService;

    @Autowired
    private StockExecutionService executionService;

    @Autowired
    private TradingDayRepository tradingDayRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("09:00 KST에 진행 중인 리그가 다음 거래일로 전환되어 직전 거래일이 마감된다")
    void changeToNextDay() {
        League league = startedLeague();

        assertThat(useCase.change(league.getId(), DAY2.minusSeconds(1))).isZero();
        assertThat(useCase.change(league.getId(), DAY2)).isEqualTo(1);

        assertThat(closedDayNumbers(league)).containsExactly(1);
    }

    @Test
    @DisplayName("같은 거래일 전환을 두 번 실행해도 한 번만 처리된다")
    void changeIsIdempotent() {
        League league = startedLeague();

        assertThat(useCase.change(league.getId(), DAY2)).isEqualTo(1);
        assertThat(useCase.change(league.getId(), DAY2.plusSeconds(5))).isZero();

        assertThat(closedDayNumbers(league)).containsExactly(1);
    }

    @Test
    @DisplayName("서버가 내려가 놓친 거래일 전환은 다음 실행에서 순서대로 보정된다")
    void catchUpMissedChanges() {
        League league = startedLeague();

        assertThat(useCase.change(league.getId(), DAY2.plusDays(2).plusSeconds(1))).isEqualTo(3);

        assertThat(closedDayNumbers(league)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("종료 시각 이후 기동해도 7일차 전까지 밀린 전환을 보정하고 7일차는 마감하지 않는다")
    void catchUpAfterEnd() {
        League league = startedLeague();
        useCase.change(league.getId(), DAY2.plusDays(2));

        assertThat(useCase.change(league.getId(), league.getEndsAt().plusDays(1))).isEqualTo(3);

        assertThat(closedDayNumbers(league)).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("대기실·만료 리그는 거래일 전환을 하지 않는다")
    void skipNotStarted() {
        League waiting = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("AAAAAAA"), STARTED_AT));
        League expired = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("BBBBBBB"), STARTED_AT));
        ReflectionTestUtils.setField(expired, "status", LeagueStatus.EXPIRED);
        entityManager.flush();

        assertThat(useCase.change(waiting.getId(), DAY2.plusDays(3))).isZero();
        assertThat(useCase.change(expired.getId(), DAY2.plusDays(3))).isZero();
        assertThat(useCase.change(999_999L, DAY2)).isZero();
    }

    @Test
    @DisplayName("09:00 거래일 전환에서 종목 종가가 확정되고 새 기준가가 적용된다")
    void closesStockPricesOnChange() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        executionService.applyExecution(league.getId(), stockId, 1, 11_000, 3);

        useCase.change(league.getId(), DAY2);

        assertThat(dayPrice(stockId, 1).getClosePrice()).isEqualTo(11_000);
        assertThat(dayPrice(stockId, 2).getBasePrice()).isEqualTo(11_000);
        assertThat(dayPrice(stockId, 2).getUpperLimitPrice()).isEqualTo(14_300);
    }

    @Test
    @DisplayName("놓친 전환을 보정하면 체결 없던 거래일의 종가는 직전 종가와 같다")
    void catchUpChainsClosePrices() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        executionService.applyExecution(league.getId(), stockId, 1, 11_000, 3);

        useCase.change(league.getId(), DAY2.plusDays(2).plusSeconds(1));

        assertThat(dayPrice(stockId, 1).getClosePrice()).isEqualTo(11_000);
        assertThat(dayPrice(stockId, 2).getClosePrice()).isEqualTo(11_000);
        assertThat(dayPrice(stockId, 3).getClosePrice()).isEqualTo(11_000);
        assertThat(dayPrice(stockId, 4).getBasePrice()).isEqualTo(11_000);
    }

    @Test
    @DisplayName("종목 시세 전환이 실패하면 거래일 마감도 롤백된다")
    void rollbackOnMarketFailure() {
        League league = startedLeague();
        Stock stock = Stock.list(league.getId(), 10L, StockName.of("가나다"),
                StockDescription.of("소개"));
        stock.open();
        entityManager.persistAndFlush(stock);

        assertThatThrownBy(() -> useCase.change(league.getId(), DAY2))
                .isInstanceOf(IllegalStateException.class);

        // 서비스 트랜잭션이 테스트 트랜잭션에 참여하므로 메모리 상태로 롤백 대상(마감 기록 전)을 확인한다
        assertThat(closedDayNumbers(league)).isEmpty();
    }

    @Test
    @DisplayName("09:00 거래일 전환에서 이전 거래일 지정가 미체결 주문이 만료되고 예약 자산이 해제된다")
    void expiresOpenOrdersOnChange() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        Order buy = reservedOrder(league, stockId, BUYER, OrderSide.BUY, 1);
        final Order sell = reservedOrder(league, stockId, SELLER, OrderSide.SELL, 1);

        useCase.change(league.getId(), DAY2.minusSeconds(1));
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(account(league, BUYER).getReservedDeposit()).isEqualTo(90_000);

        useCase.change(league.getId(), DAY2);

        assertThat(buy.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(sell.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(account(league, BUYER).getReservedDeposit()).isZero();
        assertThat(holding(league, SELLER).getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("놓친 전환을 보정해도 만료 일시는 각 거래일의 09:00:00이다")
    void catchUpExpiresWithEachDayStart() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        Order day1 = reservedOrder(league, stockId, BUYER, OrderSide.BUY, 1);
        Order day2 = reservedOrder(league, stockId, BUYER, OrderSide.BUY, 2);

        useCase.change(league.getId(), DAY2.plusDays(2).plusSeconds(1));

        assertThat(day1.getCanceledAt()).isEqualTo(DAY2);
        assertThat(day2.getCanceledAt()).isEqualTo(DAY2.plusDays(1));
        assertThat(account(league, BUYER).getReservedDeposit()).isZero();
    }

    @Test
    @DisplayName("미체결 만료 중 예약 해제가 실패하면 종가 확정과 거래일 마감도 롤백된다")
    void rollbackOnExpirationFailure() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        executionService.applyExecution(league.getId(), stockId, 1, 11_000, 3);
        // 예약 없이 열린 매도 주문을 만들어 해제 시 데이터 오류를 일으킨다
        entityManager.persistAndFlush(Account.open(league.getId(), SELLER, 1_000_000));
        entityManager.persistAndFlush(Holding.allocate(league.getId(), SELLER, stockId, 100));
        entityManager.persistAndFlush(Order.limit(league.getId(), SELLER, stockId,
                OrderSide.SELL, 10_000, 10, 1, STARTED_AT));

        assertThatThrownBy(() -> useCase.change(league.getId(), DAY2))
                .isInstanceOf(NaspiRuntimeException.class);

        // 서비스 트랜잭션이 테스트 트랜잭션에 참여하므로 마감 기록 전 상태를 메모리로 확인한다
        assertThat(closedDayNumbers(league)).isEmpty();
    }

    private Order reservedOrder(League league, long stockId, long playerId, OrderSide side,
            int tradingDay) {
        if (entityManager.getEntityManager().createQuery(
                "select count(a) from Account a where a.leagueId = :l and a.playerId = :p",
                Long.class).setParameter("l", league.getId()).setParameter("p", playerId)
                .getSingleResult() == 0) {
            entityManager.persist(Account.open(league.getId(), playerId, 1_000_000));
            entityManager.persist(Holding.allocate(league.getId(), playerId, stockId,
                    playerId == LISTER ? 3_000 : playerId == SELLER ? 100 : 0));
            entityManager.flush();
        }
        Order order = Order.limit(league.getId(), playerId, stockId, side, 9_000, 10, tradingDay,
                STARTED_AT);
        if (side == OrderSide.BUY) {
            reservationService.reserveCashForBuy(league.getId(), playerId, 9_000, 10);
            order.recordReservedPrice(9_000);
        } else {
            reservationService.reserveStockForSell(league.getId(), playerId, stockId, 10);
        }
        return entityManager.persistAndFlush(order);
    }

    private Account account(League league, long playerId) {
        return entityManager.getEntityManager().createQuery(
                        "select a from Account a where a.leagueId = :l and a.playerId = :p",
                        Account.class)
                .setParameter("l", league.getId()).setParameter("p", playerId).getSingleResult();
    }

    private Holding holding(League league, long playerId) {
        return entityManager.getEntityManager().createQuery(
                        "select h from Holding h where h.leagueId = :l and h.playerId = :p",
                        Holding.class)
                .setParameter("l", league.getId()).setParameter("p", playerId).getSingleResult();
    }

    private long stockWithFirstDay(League league) {
        Stock stock = Stock.list(league.getId(), 10L, StockName.of("가나다"),
                StockDescription.of("소개"));
        stock.open();
        long stockId = entityManager.persistAndGetId(stock, Long.class);
        entityManager.persist(StockDailyPrice.open(league.getId(), stockId, 1, 10_000));
        entityManager.flush();
        return stockId;
    }

    private StockDailyPrice dayPrice(long stockId, int tradingDay) {
        entityManager.flush();
        return entityManager.getEntityManager().createQuery(
                        "select d from StockDailyPrice d where d.stockId = :s "
                                + "and d.tradingDay = :t", StockDailyPrice.class)
                .setParameter("s", stockId).setParameter("t", tradingDay).getSingleResult();
    }

    private List<Integer> closedDayNumbers(League league) {
        entityManager.flush();
        entityManager.clear();
        return tradingDayRepository.findAllByLeagueIdOrderByDayNumber(league.getId()).stream()
                .filter(TradingDay::isClosed)
                .map(TradingDay::getDayNumber)
                .toList();
    }

    private League startedLeague() {
        League league = entityManager.persistAndFlush(League.create(
                LeagueName.of("리그"), InviteCode.of("CCCCCCC"), STARTED_AT.minusDays(1)));
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
        return entityManager.persistAndFlush(league);
    }
}
