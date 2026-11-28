package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.DailyAssetSnapshot;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({TradingDayChangeUseCase.class, StockTradingDayService.class,
        OrderClosingTestConfig.class, MutableClock.Config.class, DailyAssetSnapshotUseCase.class,
        AssetQueryService.class})
class TradingDayChangeUseCaseTest {

    /** 시작 후 1일차: 2026-11-15 10:00 시작, 2일차는 11-16 09:00. */
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 16, 9, 0);

    private static final long LISTER = 10L;
    private static final long SELLER = 20L;
    private static final long BUYER = 30L;

    @MockitoBean
    private MemberService memberService;

    @MockitoSpyBean
    private AssetQueryService assetQueryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Autowired
    private ApplicationEvents events;

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

    @Test
    @DisplayName("거래일이 전환되면 새 거래일 일차와 주문이 만료된 플레이어를 담은 거래일 전환 이벤트를 한 번 발행한다")
    void publishTradingDayChanged() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        reservedOrder(league, stockId, BUYER, OrderSide.BUY, 1);
        reservedOrder(league, stockId, BUYER, OrderSide.BUY, 1);

        useCase.change(league.getId(), DAY2);

        assertThat(events.stream(TradingDayChanged.class))
                .containsExactly(new TradingDayChanged(league.getId(), 2, Set.of(BUYER)));
    }

    @Test
    @DisplayName("놓친 전환을 여러 거래일 보정해도 거래일 전환 이벤트는 리그당 한 번이고 만료된 플레이어를 모두 담는다")
    void publishOnceOnCatchUp() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        reservedOrder(league, stockId, BUYER, OrderSide.BUY, 1);
        reservedOrder(league, stockId, SELLER, OrderSide.SELL, 2);

        useCase.change(league.getId(), DAY2.plusDays(2).plusSeconds(1));

        assertThat(events.stream(TradingDayChanged.class))
                .containsExactly(new TradingDayChanged(league.getId(), 4, Set.of(BUYER, SELLER)));
    }

    @Test
    @DisplayName("처리할 전환이 없으면 거래일 전환 이벤트를 발행하지 않는다")
    void noEventWithoutChange() {
        League league = startedLeague();

        useCase.change(league.getId(), DAY2.minusSeconds(1));

        assertThat(events.stream(TradingDayChanged.class)).isEmpty();
    }

    @Test
    @DisplayName("리그 종료 시각이 지나 밀린 전환을 보정할 때는 거래일 갱신 알림 이벤트를 발행하지 않는다")
    void noEventAfterEnd() {
        League league = startedLeague();

        assertThat(useCase.change(league.getId(), league.getEndsAt())).isEqualTo(6);

        assertThat(events.stream(TradingDayChanged.class)).isEmpty();
    }

    @Test
    @DisplayName("09:00 거래일 전환에서 직전 거래일 종가 기준으로 참여자별 현금·주식 평가금액·총자산이 집계된다")
    void recordsDailyAssetAtClose() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        asset(league, LISTER, 50_000_000, stockId, 3_000);
        asset(league, BUYER, 100_000_000, stockId, 0);
        executionService.applyExecution(league.getId(), stockId, 1, 11_000, 3);

        useCase.change(league.getId(), DAY2);

        assertThat(snapshots(league, 1)).extracting(DailyAssetSnapshot::getPlayerId,
                        DailyAssetSnapshot::getCash, DailyAssetSnapshot::getStockValuation,
                        DailyAssetSnapshot::getTotalAsset)
                .containsExactly(
                        tuple(LISTER, 50_000_000L, 33_000_000L, 83_000_000L),
                        tuple(BUYER, 100_000_000L, 0L, 100_000_000L));
        assertThat(snapshots(league, 2)).isEmpty();
    }

    @Test
    @DisplayName("같은 거래일 전환을 두 번 실행해도 거래일별 자산 집계는 한 번만 저장된다")
    void recordsOnceWhenRunTwice() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        asset(league, LISTER, 50_000_000, stockId, 3_000);
        asset(league, BUYER, 100_000_000, stockId, 0);

        useCase.change(league.getId(), DAY2);
        useCase.change(league.getId(), DAY2.plusSeconds(5));

        assertThat(snapshots(league, 1)).hasSize(2);
    }

    @Test
    @DisplayName("놓친 거래일 전환을 보정하면 거래일마다 자산 집계가 저장되고 체결이 없던 거래일은 직전 종가로 평가된다")
    void catchUpRecordsEachDay() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        asset(league, LISTER, 50_000_000, stockId, 3_000);
        executionService.applyExecution(league.getId(), stockId, 1, 11_000, 3);

        useCase.change(league.getId(), DAY2.plusDays(2).plusSeconds(1));

        for (int day = 1; day <= 3; day++) {
            assertThat(snapshots(league, day)).extracting(DailyAssetSnapshot::getStockValuation,
                            DailyAssetSnapshot::getTotalAsset)
                    .containsExactly(tuple(33_000_000L, 83_000_000L));
        }
        assertThat(snapshots(league, 4)).isEmpty();
    }

    @Test
    @DisplayName("미체결 주문 만료 전 거래일 종료 시점 자산으로 집계해도 총자산은 예약 해제와 무관하다")
    void recordsBeforeExpiration() {
        League league = startedLeague();
        long stockId = stockWithFirstDay(league);
        reservedOrder(league, stockId, BUYER, OrderSide.BUY, 1);
        reservedOrder(league, stockId, SELLER, OrderSide.SELL, 1);

        useCase.change(league.getId(), DAY2);

        assertThat(account(league, BUYER).getReservedDeposit()).isZero();
        assertThat(holding(league, SELLER).getReservedQuantity()).isZero();
        // 예약 해제 전후 모두 예수금·보유 수량은 예약분을 포함한 전체 값이다
        assertThat(snapshots(league, 1)).extracting(DailyAssetSnapshot::getPlayerId,
                        DailyAssetSnapshot::getCash, DailyAssetSnapshot::getStockValuation,
                        DailyAssetSnapshot::getTotalAsset)
                .containsExactly(
                        tuple(SELLER, 1_000_000L, 1_000_000L, 2_000_000L),
                        tuple(BUYER, 1_000_000L, 0L, 1_000_000L));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("자산 집계가 실패하면 종가 확정과 거래일 마감도 롤백된다")
    void rollbackOnAggregationFailure() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            long[] ids = tx.execute(status -> {
                League league = startedLeague();
                long stockId = stockWithFirstDay(league);
                asset(league, LISTER, 50_000_000, stockId, 3_000);
                executionService.applyExecution(league.getId(), stockId, 1, 11_000, 3);
                return new long[] {league.getId(), stockId};
            });
            willThrow(new NaspiRuntimeException("집계 실패"))
                    .given(assetQueryService).aggregateTotalAssets(anyLong(), any());

            assertThatThrownBy(() -> useCase.change(ids[0], DAY2))
                    .isInstanceOf(NaspiRuntimeException.class);

            tx.executeWithoutResult(status -> {
                League league = entityManager.find(League.class, ids[0]);
                assertThat(closedDayNumbers(league)).isEmpty();
                assertThat(dayPrice(ids[1], 1).getClosePrice()).isNull();
                assertThat(dailyPriceCount(ids[1])).isEqualTo(1);
                assertThat(snapshots(league, 1)).isEmpty();
            });
        } finally {
            tx.executeWithoutResult(status -> {
                for (String entity : List.of("DailyAssetSnapshot", "StockDailyPrice", "Stock",
                        "Holding", "Account", "TradingDay", "Player", "League")) {
                    entityManager.getEntityManager().createQuery("delete from " + entity)
                            .executeUpdate();
                }
            });
        }
    }

    private void asset(League league, long playerId, long cash, long stockId, long quantity) {
        entityManager.persist(Account.open(league.getId(), playerId, cash));
        if (quantity > 0) {
            entityManager.persist(Holding.allocate(league.getId(), playerId, stockId, quantity));
        }
        entityManager.flush();
    }

    private List<DailyAssetSnapshot> snapshots(League league, int tradingDay) {
        entityManager.flush();
        return entityManager.getEntityManager().createQuery(
                        "select d from DailyAssetSnapshot d where d.leagueId = :l "
                                + "and d.tradingDay = :t order by d.playerId",
                        DailyAssetSnapshot.class)
                .setParameter("l", league.getId()).setParameter("t", tradingDay)
                .getResultList();
    }

    private long dailyPriceCount(long stockId) {
        return entityManager.getEntityManager().createQuery(
                        "select count(d) from StockDailyPrice d where d.stockId = :s", Long.class)
                .setParameter("s", stockId).getSingleResult();
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
