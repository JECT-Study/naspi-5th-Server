package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueResultPlayer;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.league.domain.TradingDayRepository;
import kr.ject.naspi.market.application.FinalEvaluationPriceUseCase;
import kr.ject.naspi.market.application.StockExecutionService;
import kr.ject.naspi.market.application.StockTradingDayService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.application.OrderClosingTestConfig;
import kr.ject.naspi.order.application.TradeQueryService;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({LeagueEndUseCase.class, TradingDayChangeUseCase.class, StockTradingDayService.class,
        OrderClosingTestConfig.class, LeagueJoinLimit.class, MutableClock.Config.class,
        LeagueResultUseCase.class, FinalEvaluationPriceUseCase.class, TradeQueryService.class,
        AssetQueryService.class})
class LeagueEndUseCaseTest {

    /** 2026-11-15 10:00 시작: 종료 시각은 11-22 09:00. */
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 11, 22, 9, 0);

    private static final long HOST_MEMBER = 1L;
    /** 결과 대상 참여자(플레이어 행)와 겹치지 않는 플레이어 ID. 계좌만 있고 결과 대상은 아니다. */
    private static final long LISTER = 10_000L;
    private static final long SELLER = 20_000L;
    private static final long BUYER = 30_000L;

    private static final long INITIAL_CASH = 100_000_000L;

    @MockitoBean
    private MemberService memberService;

    @MockitoSpyBean
    private AssetQueryService assetQueryService;

    @Autowired
    private LeagueEndUseCase useCase;

    @Autowired
    private TradingDayChangeUseCase tradingDayChangeUseCase;

    @Autowired
    private AssetReservationService reservationService;

    @Autowired
    private StockExecutionService executionService;

    @Autowired
    private TradingDayRepository tradingDayRepository;

    @Autowired
    private LeagueJoinLimit joinLimit;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void stubNames() {
        given(memberService.getNames(any())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(), id -> "회원" + id));
        });
    }

    @Test
    @DisplayName("종료 시각에 리그가 종료되고 7일차 종가가 확정된다")
    void endAtEndsAt() {
        League league = startedLeague("AAAAAAA");
        long stockId = stockWithFirstDay(league);
        tradingDayChangeUseCase.change(league.getId(), ENDS_AT.minusDays(1));
        executionService.applyExecution(league.getId(), stockId, 7, 11_000, 3);

        assertThat(useCase.end(league.getId(), ENDS_AT)).isTrue();

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.ENDED);
        assertThat(league.getInviteCode()).isNull();
        assertThat(dayPrice(stockId, 7).getClosePrice()).isEqualTo(11_000);
        assertThat(tradingDay(league, 7).getClosedAt()).isEqualTo(ENDS_AT);
        assertThat(events.stream(LeagueEnded.class))
                .containsExactly(new LeagueEnded(league.getId(), ENDS_AT));
    }

    @Test
    @DisplayName("종료 시각 전에는 종료 처리하지 않는다")
    void notYetEndsAt() {
        League league = startedLeague("AAAAAAA");

        assertThat(useCase.end(league.getId(), ENDS_AT.minusSeconds(1))).isFalse();

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
        assertThat(events.stream(LeagueEnded.class)).isEmpty();
    }

    @Test
    @DisplayName("리그 종료를 두 번 실행해도 한 번만 처리된다")
    void idempotent() {
        League league = startedLeague("AAAAAAA");

        assertThat(useCase.end(league.getId(), ENDS_AT)).isTrue();
        assertThatCode(() -> assertThat(useCase.end(league.getId(), ENDS_AT.plusSeconds(5)))
                .isFalse()).doesNotThrowAnyException();

        assertThat(tradingDay(league, 7).getClosedAt()).isEqualTo(ENDS_AT);
        assertThat(events.stream(LeagueEnded.class)).hasSize(1);
        assertThat(resultCount(league)).isEqualTo(1);
    }

    @Test
    @DisplayName("리그가 종료되면 미체결 주문 취소 뒤 최종 결과가 한 번 저장된다")
    void endCreatesResult() {
        League league = startedLeague("AAAAAAA");
        long stockId = stockWithFirstDay(league);
        tradingDayChangeUseCase.change(league.getId(), ENDS_AT.minusDays(1));
        List<Player> players = players(league);
        Order buy = reservedOrder(league, stockId, players.get(0).getId(), OrderSide.BUY, 7);
        assertThat(account(league, players.get(0).getId()).getReservedDeposit()).isPositive();

        useCase.end(league.getId(), ENDS_AT.plusMinutes(1));

        assertThat(buy.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(resultCount(league)).isEqualTo(1);
        List<LeagueResultPlayer> results = entityManager.getEntityManager().createQuery(
                        "select p from LeagueResultPlayer p where p.leagueId = :l order by p.id",
                        LeagueResultPlayer.class)
                .setParameter("l", league.getId()).getResultList();
        assertThat(results).extracting(LeagueResultPlayer::getPlayerId)
                .containsExactlyInAnyOrderElementsOf(players.stream().map(Player::getId).toList());
        // 예약이 해제된 전체 현금이 최종 현금이다
        assertThat(results).allSatisfy(result -> {
            assertThat(result.getFinalCash()).isEqualTo(INITIAL_CASH);
            assertThat(result.getFinalRank()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("결과 산정 중 오류가 나면 리그는 종료되지 않고 결과도 저장되지 않는다")
    void rollbackOnResultFailure() {
        League league = startedLeague("AAAAAAA");
        willThrow(new NaspiRuntimeException("집계 실패")).given(assetQueryService)
                .aggregateTotalAssets(anyLong(), any());

        assertThatThrownBy(() -> useCase.end(league.getId(), ENDS_AT))
                .isInstanceOf(NaspiRuntimeException.class);

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
        assertThat(events.stream(LeagueEnded.class)).isEmpty();
        assertThat(resultCount(league)).isZero();
    }

    @Test
    @DisplayName("서버가 내려가 놓친 거래일 전환은 리그 종료 전에 순서대로 보정된다")
    void catchUpMissedTradingDays() {
        League league = startedLeague("AAAAAAA");
        long stockId = stockWithFirstDay(league);
        executionService.applyExecution(league.getId(), stockId, 1, 11_000, 3);
        // 4일차까지 마감된 상태 (5일차 시작 직후)
        tradingDayChangeUseCase.change(league.getId(), ENDS_AT.minusDays(3));
        assertThat(closedDayNumbers(league)).containsExactly(1, 2, 3, 4);

        assertThat(useCase.end(league.getId(), ENDS_AT.plusDays(1))).isTrue();

        assertThat(closedDayNumbers(league)).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(dayPrice(stockId, 7).getBasePrice()).isEqualTo(11_000);
        assertThat(dayPrice(stockId, 7).getClosePrice()).isEqualTo(11_000);
        assertThat(entityManager.find(League.class, league.getId()).getStatus())
                .isEqualTo(LeagueStatus.ENDED);
    }

    @Test
    @DisplayName("리그가 종료되면 모든 미체결 지정가 주문이 리그 종료 사유로 취소되고 예약 자산이 해제된다")
    void cancelOpenOrders() {
        League league = startedLeague("AAAAAAA");
        long stockId = stockWithFirstDay(league);
        tradingDayChangeUseCase.change(league.getId(), ENDS_AT.minusDays(1));
        // 거래일 구분 없이 남은 주문을 모두 취소한다
        Order day1 = reservedOrder(league, stockId, BUYER, OrderSide.BUY, 1);
        Order day7 = reservedOrder(league, stockId, BUYER, OrderSide.BUY, 7);
        Order sell = reservedOrder(league, stockId, SELLER, OrderSide.SELL, 7);

        useCase.end(league.getId(), ENDS_AT.plusMinutes(3));

        for (Order order : List.of(day1, day7, sell)) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            assertThat(order.getCancelReason()).isEqualTo(CancelReason.LEAGUE_ENDED);
            assertThat(order.getCanceledAt()).isEqualTo(ENDS_AT);
        }
        assertThat(account(league, BUYER).getReservedDeposit()).isZero();
        assertThat(holding(league, SELLER).getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("대기실·만료 리그는 종료 처리하지 않는다")
    void skipNotStarted() {
        League waiting = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("BBBBBBB"), STARTED_AT));
        League expired = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("CCCCCCC"), STARTED_AT));
        ReflectionTestUtils.setField(expired, "status", LeagueStatus.EXPIRED);
        entityManager.flush();

        assertThat(useCase.end(waiting.getId(), ENDS_AT.plusDays(10))).isFalse();
        assertThat(useCase.end(expired.getId(), ENDS_AT.plusDays(10))).isFalse();
        assertThat(useCase.end(999_999L, ENDS_AT)).isFalse();
        assertThat(waiting.getStatus()).isEqualTo(LeagueStatus.WAITING);
    }

    @Test
    @DisplayName("종료 처리 중 오류가 나면 리그는 종료되지 않고 다음 실행에서 다시 처리된다")
    void rollbackOnFailure() {
        League league = startedLeague("AAAAAAA");
        // 7일차 시세 행이 없는 종목
        Stock stock = Stock.list(league.getId(), LISTER, StockName.of("가나다"),
                StockDescription.of("소개"));
        stock.open();
        entityManager.persist(stock);
        tradingDayRepository.findAllByLeagueIdOrderByDayNumber(league.getId()).stream()
                .filter(day -> !day.isLast())
                .forEach(day -> day.close(day.getEndsAt()));
        entityManager.flush();

        assertThatThrownBy(() -> useCase.end(league.getId(), ENDS_AT))
                .isInstanceOf(IllegalStateException.class);

        // 서비스 트랜잭션이 테스트 트랜잭션에 참여하므로 메모리 상태로 롤백 대상(종료 전환 전)을 확인한다
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
        assertThat(tradingDay(league, 7).isClosed()).isFalse();
        assertThat(events.stream(LeagueEnded.class)).isEmpty();
    }

    @Test
    @DisplayName("종료된 리그는 동시 참여 리그 수에서 제외된다")
    void joinLimitReleased() {
        final League started = startedLeague("AAAAAAA");
        for (String code : List.of("BBBBBBB", "CCCCCCC", "DDDDDDD", "EEEEEEE")) {
            League waiting = entityManager.persist(
                    League.create(LeagueName.of("리그"), InviteCode.of(code), STARTED_AT));
            entityManager.persist(Player.enter(waiting.getId(), HOST_MEMBER, STARTED_AT));
        }
        entityManager.flush();
        assertThatThrownBy(() -> joinLimit.check(HOST_MEMBER))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_JOIN_LIMIT_EXCEEDED);

        useCase.end(started.getId(), ENDS_AT);
        entityManager.flush();

        assertThatCode(() -> joinLimit.check(HOST_MEMBER)).doesNotThrowAnyException();
    }

    private Order reservedOrder(League league, long stockId, long playerId, OrderSide side,
            int tradingDay) {
        if (entityManager.getEntityManager().createQuery(
                "select count(a) from Account a where a.leagueId = :l and a.playerId = :p",
                Long.class).setParameter("l", league.getId()).setParameter("p", playerId)
                .getSingleResult() == 0) {
            entityManager.persist(Account.open(league.getId(), playerId, 1_000_000));
            entityManager.persist(Holding.allocate(league.getId(), playerId, stockId,
                    playerId == SELLER ? 100 : 0));
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

    private long resultCount(League league) {
        return entityManager.getEntityManager().createQuery(
                        "select count(r) from LeagueResult r where r.leagueId = :l", Long.class)
                .setParameter("l", league.getId()).getSingleResult();
    }

    private List<Player> players(League league) {
        return entityManager.getEntityManager().createQuery(
                        "select p from Player p where p.leagueId = :l order by p.id", Player.class)
                .setParameter("l", league.getId()).getResultList();
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
        Stock stock = Stock.list(league.getId(), LISTER, StockName.of("가나다"),
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

    private TradingDay tradingDay(League league, int dayNumber) {
        return tradingDayRepository.findByLeagueIdAndDayNumber(league.getId(), dayNumber)
                .orElseThrow();
    }

    private List<Integer> closedDayNumbers(League league) {
        return tradingDayRepository.findAllByLeagueIdOrderByDayNumber(league.getId()).stream()
                .filter(TradingDay::isClosed)
                .map(TradingDay::getDayNumber)
                .toList();
    }

    /** 참여자 3명(방장 회원 포함)이 저장되고 초기 현금 1억 원 계좌가 있는 진행 중인 리그. */
    private League startedLeague(String code) {
        League league = entityManager.persistAndFlush(League.create(
                LeagueName.of("리그"), InviteCode.of(code), STARTED_AT.minusDays(1)));
        List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(league.getId(), memberId,
                            STARTED_AT.minusDays(1));
                    player.completeListing(STARTED_AT.minusDays(1));
                    return entityManager.persist(player);
                })
                .toList();
        players.forEach(player -> entityManager.persist(
                Account.open(league.getId(), player.getId(), INITIAL_CASH)));
        league.start(players, STARTED_AT);
        tradingDayRepository.saveAll(TradingDay.of(league.getId(), league.tradingSchedule()));
        return entityManager.persistAndFlush(league);
    }
}
