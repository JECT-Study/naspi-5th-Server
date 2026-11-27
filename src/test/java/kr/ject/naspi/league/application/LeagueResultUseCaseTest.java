package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.domain.Account;
import kr.ject.naspi.asset.domain.Holding;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.domain.DailyAssetSnapshot;
import kr.ject.naspi.league.domain.LeagueResult;
import kr.ject.naspi.league.domain.LeagueResultHolding;
import kr.ject.naspi.league.domain.LeagueResultPlayer;
import kr.ject.naspi.league.domain.LeagueResultStock;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.FinalEvaluationPriceUseCase;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.order.application.TradeQueryService;
import kr.ject.naspi.order.domain.Execution;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 결과 산정(LEA-13·INT-05). 종료 평가 가격(market)과 총자산 집계(asset)를 실제로 호출해 저장된 결과를
 * 확인한다. 리그 종료 트랜잭션 안에서 호출되는 형태로 테스트 트랜잭션 안에서 호출한다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueResultUseCase.class, FinalEvaluationPriceUseCase.class, TradeQueryService.class,
        AssetQueryService.class, StockQueryService.class, LocalStockSnapshotCache.class,
        DailyAssetSnapshotUseCase.class})
class LeagueResultUseCaseTest {

    private static final long LEAGUE_ID = 1L;
    private static final int LAST_DAY = 7;
    private static final LocalDateTime LISTED_AT = LocalDateTime.of(2026, 11, 14, 10, 0);
    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 11, 22, 9, 0);
    private static final LocalDateTime FINALIZED_AT = ENDS_AT.plusMinutes(1);
    private static final LocalDateTime DAY7 = LocalDateTime.of(2026, 11, 21, 10, 0);

    @MockitoBean
    private MemberService memberService;

    @Autowired
    private LeagueResultUseCase useCase;

    @Autowired
    private TestEntityManager entityManager;

    private long memberSeq = 0;

    @BeforeEach
    void stubNames() {
        given(memberService.getNames(any())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(Collectors.toMap(Function.identity(), id -> "회원" + id));
        });
    }

    @Test
    @DisplayName("최종 총자산은 최종 현금과 각 종목 보유 수량 × 종료 평가 가격(VWAP)의 합이다")
    void totalAssetUsesFinalEvaluationPrice() {
        Player lister = player(LISTED_AT);
        Player holder = player(LISTED_AT);
        final Player cashOnly = player(LISTED_AT);
        long stockId = stock(lister);
        account(lister, 50_000_000);
        holding(lister, stockId, 2_500);
        account(holder, 60_000_000);
        holding(holder, stockId, 1_000);
        account(cashOnly, 70_000_000);
        // 7일차 500주: (300 × 10,000 + 200 × 11,000) / 500 = 10,400. 현재가(마지막 체결가)는 11,000
        trade(stockId, LAST_DAY, 10_000, 300, DAY7);
        trade(stockId, LAST_DAY, 11_000, 200, DAY7.plusMinutes(1));
        entityManager.find(Stock.class, stockId).applyTrade(11_000);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(resultStocks()).extracting(LeagueResultStock::getStockId,
                        LeagueResultStock::getFinalEvaluationPrice)
                .containsExactly(tuple(stockId, 10_400L));
        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getPlayerId,
                        LeagueResultPlayer::getFinalTotalAsset, LeagueResultPlayer::getFinalRank)
                .containsExactly(
                        tuple(lister.getId(), 50_000_000L + 2_500L * 10_400, 1),
                        tuple(holder.getId(), 60_000_000L + 1_000L * 10_400, 2),
                        tuple(cashOnly.getId(), 70_000_000L, 3));
        LeagueResult result = result();
        assertThat(result.getFinalizedAt()).isEqualTo(FINALIZED_AT);
    }

    @Test
    @DisplayName("자사주도 종료 평가 가격으로 평가해 최종 총자산에 포함한다")
    void includesTreasuryStock() {
        Player lister = player(LISTED_AT);
        Player other = player(LISTED_AT);
        long stockId = stock(lister);
        account(lister, 10_000_000);
        holding(lister, stockId, 3_000);
        account(other, 30_000_000);
        trade(stockId, LAST_DAY, 12_000, 500, DAY7);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        LeagueResultPlayer listerResult = resultPlayer(lister);
        assertThat(listerResult.getFinalTotalAsset()).isEqualTo(10_000_000L + 3_000L * 12_000);
        assertThat(listerResult.getFinalRank()).isEqualTo(1);
        assertThat(holdingsOf(listerResult)).extracting(LeagueResultHolding::getStockId,
                LeagueResultHolding::getValuation).containsExactly(tuple(stockId, 36_000_000L));
    }

    @Test
    @DisplayName("최종 총자산이 같은 참여자는 공동 순위이고 다음 순위는 동점자 수만큼 건너뛴다")
    void jointRank() {
        Player first = player(LISTED_AT);
        Player second = player(LISTED_AT);
        Player third = player(LISTED_AT);
        account(first, 100_000_000);
        account(second, 100_000_000);
        account(third, 90_000_000);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getFinalRank)
                .containsExactly(1, 1, 3);
    }

    @Test
    @DisplayName("공동 순위인 참여자는 상장 일시가 이른 참여자를 먼저 나열하고, 상장 일시도 같으면 플레이어 ID 순이다")
    void jointRankOrderedByListedAt() {
        Player laterListed = player(LISTED_AT.plusHours(1));
        Player sameListedA = player(LISTED_AT);
        Player sameListedB = player(LISTED_AT);
        account(laterListed, 100_000_000);
        account(sameListedA, 100_000_000);
        account(sameListedB, 100_000_000);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getPlayerId,
                        LeagueResultPlayer::getFinalRank)
                .containsExactly(tuple(sameListedA.getId(), 1), tuple(sameListedB.getId(), 1),
                        tuple(laterListed.getId(), 1));
    }

    @ParameterizedTest
    @CsvSource({
        "87650000, -12350000, -12.4",
        "112345000, 12345000, 12.3",
        "112350000, 12350000, 12.4",
        "100000000, 0, 0.0"
    })
    @DisplayName("최종 손익은 최종 총자산 − 1억 원, 최종 수익률은 소수점 둘째 자리에서 반올림하고 음수는 절댓값 기준으로 반올림한다")
    void profitAndReturnRate(long totalAsset, long profit, String returnRate) {
        Player target = player(LISTED_AT);
        account(target, totalAsset);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        LeagueResultPlayer result = resultPlayer(target);
        assertThat(result.getFinalTotalAsset()).isEqualTo(totalAsset);
        assertThat(result.getFinalProfit()).isEqualTo(profit);
        assertThat(result.getFinalReturnRate()).isEqualByComparingTo(new BigDecimal(returnRate));
        assertThat(result.getFinalReturnRate().scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("리그 종료 전에 탈퇴한 참여자는 순위·결과에서 제외되고 남은 참여자로 순위를 매긴다")
    void excludesWithdrawnBeforeEnd() {
        Player withdrawn = player(LISTED_AT);
        Player first = player(LISTED_AT);
        Player second = player(LISTED_AT);
        final Player waiting = entityManager.persist(
                Player.enter(LEAGUE_ID, ++memberSeq, LISTED_AT));
        account(withdrawn, 200_000_000);
        account(first, 100_000_000);
        account(second, 90_000_000);
        withdrawn.withdraw(ENDS_AT.minusSeconds(1));
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getPlayerId,
                        LeagueResultPlayer::getFinalRank)
                .containsExactly(tuple(first.getId(), 1), tuple(second.getId(), 2));
        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getPlayerId)
                .doesNotContain(waiting.getId());
    }

    @Test
    @DisplayName("마지막 거래일 자산 집계는 종료 평가 가격으로 산정한 최종 총자산과 같다")
    void savesLastDayFromFinalResult() {
        Player lister = player(LISTED_AT);
        Player cashOnly = player(LISTED_AT);
        long stockId = stock(lister);
        account(lister, 50_000_000);
        holding(lister, stockId, 2_500);
        account(cashOnly, 70_000_000);
        // 7일차 VWAP 10,400, 7일차 종가(마지막 체결가)는 11,000
        trade(stockId, LAST_DAY, 10_000, 300, DAY7);
        trade(stockId, LAST_DAY, 11_000, 200, DAY7.plusMinutes(1));
        entityManager.find(Stock.class, stockId).applyTrade(11_000);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(lastDaySnapshots()).extracting(DailyAssetSnapshot::getPlayerId,
                        DailyAssetSnapshot::getCash, DailyAssetSnapshot::getStockValuation,
                        DailyAssetSnapshot::getTotalAsset)
                .containsExactly(
                        tuple(lister.getId(), 50_000_000L, 2_500L * 10_400,
                                50_000_000L + 2_500L * 10_400),
                        tuple(cashOnly.getId(), 70_000_000L, 0L, 70_000_000L));
        for (DailyAssetSnapshot snapshot : lastDaySnapshots()) {
            assertThat(snapshot.getTotalAsset()).isEqualTo(
                    resultPlayer(entityManager.find(Player.class, snapshot.getPlayerId()))
                            .getFinalTotalAsset());
        }
    }

    @Test
    @DisplayName("종료 전 탈퇴자는 최종 결과에서 제외하지만 마지막 거래일 자산 집계에는 기록한다")
    void savesLastDayIncludingWithdrawn() {
        Player withdrawn = player(LISTED_AT);
        Player other = player(LISTED_AT);
        long stockId = stock(withdrawn);
        account(withdrawn, 0);
        holding(withdrawn, stockId, 2_000);
        account(other, 100_000_000);
        withdrawn.withdraw(ENDS_AT.minusDays(1));
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getPlayerId)
                .containsExactly(other.getId());
        assertThat(lastDaySnapshots()).extracting(DailyAssetSnapshot::getPlayerId,
                        DailyAssetSnapshot::getCash, DailyAssetSnapshot::getStockValuation,
                        DailyAssetSnapshot::getTotalAsset)
                .containsExactly(
                        tuple(withdrawn.getId(), 0L, 2_000L * 10_000, 2_000L * 10_000),
                        tuple(other.getId(), 100_000_000L, 0L, 100_000_000L));
    }

    @Test
    @DisplayName("종료 시각 정각 이후의 탈퇴는 종료 후 탈퇴로 보아 결과에 포함한다")
    void includesWithdrawnAtEnd() {
        Player withdrawnAtEnd = player(LISTED_AT);
        Player other = player(LISTED_AT);
        account(withdrawnAtEnd, 120_000_000);
        account(other, 100_000_000);
        withdrawnAtEnd.withdraw(ENDS_AT);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getPlayerId,
                        LeagueResultPlayer::getFinalRank)
                .containsExactly(tuple(withdrawnAtEnd.getId(), 1), tuple(other.getId(), 2));
    }

    @Test
    @DisplayName("결과 표시 이름은 종료 시점 회원 이름이고, 종료 처리 전에 회원 정보가 삭제된 종료 후 탈퇴자는 '탈퇴한 사용자'다")
    void displayName() {
        Player active = player(LISTED_AT);
        Player withdrawnAtEnd = player(LISTED_AT);
        account(active, 100_000_000);
        account(withdrawnAtEnd, 90_000_000);
        withdrawnAtEnd.withdraw(ENDS_AT.plusSeconds(10));
        entityManager.flush();
        willReturn(Map.of(active.getMemberId(), "홍길동")).given(memberService).getNames(any());

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getDisplayName)
                .containsExactly("홍길동", Player.WITHDRAWN_DISPLAY_NAME);
    }

    @Test
    @DisplayName("탈퇴하지 않은 참여자의 회원 이름이 없으면 산정에 실패한다")
    void failsWhenNameMissingForActivePlayer() {
        Player active = player(LISTED_AT);
        account(active, 100_000_000);
        entityManager.flush();
        willReturn(Map.of()).given(memberService).getNames(any());

        assertThatThrownBy(() -> useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT))
                .isInstanceOf(NaspiRuntimeException.class);
    }

    @Test
    @DisplayName("종료 전 탈퇴자가 상장한 종목도 종료 평가 가격을 저장하고 보유자의 평가액에 반영한다")
    void keepsStockOfWithdrawnLister() {
        Player withdrawnLister = player(LISTED_AT);
        Player holder = player(LISTED_AT);
        long stockId = stock(withdrawnLister);
        account(withdrawnLister, 0);
        holding(withdrawnLister, stockId, 2_000);
        account(holder, 50_000_000);
        holding(holder, stockId, 100);
        withdrawnLister.withdraw(ENDS_AT.minusDays(1));
        trade(stockId, LAST_DAY, 12_000, 600, DAY7);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(resultStocks()).extracting(LeagueResultStock::getStockId,
                        LeagueResultStock::getFinalEvaluationPrice)
                .containsExactly(tuple(stockId, 12_000L));
        LeagueResultPlayer holderResult = resultPlayer(holder);
        assertThat(holdingsOf(holderResult)).extracting(LeagueResultHolding::getValuation)
                .containsExactly(1_200_000L);
        assertThat(holderResult.getFinalTotalAsset()).isEqualTo(51_200_000L);
        assertThat(resultPlayers()).extracting(LeagueResultPlayer::getPlayerId)
                .containsExactly(holder.getId());
    }

    @Test
    @DisplayName("결과 스냅샷에 참여자별 현금, 종목별 보유 수량·평가액, 총자산이 저장된다")
    void snapshotDetails() {
        Player target = player(LISTED_AT);
        Player other = player(LISTED_AT);
        long stockA = stock(target);
        long stockB = stock(other);
        final long stockC = stock(player(LISTED_AT));
        account(target, 40_000_000);
        holding(target, stockA, 2_500);
        holding(target, stockB, 1_250);
        holding(target, stockC, 0);
        account(other, 50_000_000);
        account(entityManager.find(Player.class, stockLister(stockC)), 50_000_000);
        trade(stockA, LAST_DAY, 9_000, 500, DAY7);
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        LeagueResultPlayer result = resultPlayer(target);
        assertThat(result.getFinalCash()).isEqualTo(40_000_000L);
        assertThat(result.getFinalTotalAsset())
                .isEqualTo(40_000_000L + 2_500L * 9_000 + 1_250L * 10_000);
        assertThat(result.getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(result.getLeagueResultId()).isEqualTo(result().getId());
        // 보유 수량 0인 종목은 행이 없다
        assertThat(holdingsOf(result)).extracting(LeagueResultHolding::getStockId,
                        LeagueResultHolding::getQuantity, LeagueResultHolding::getValuation,
                        LeagueResultHolding::getLeagueId)
                .containsExactly(tuple(stockA, 2_500L, 22_500_000L, LEAGUE_ID),
                        tuple(stockB, 1_250L, 12_500_000L, LEAGUE_ID));
        assertThat(resultStocks()).extracting(LeagueResultStock::getStockId)
                .containsExactly(stockA, stockB, stockC);
    }

    @Test
    @DisplayName("결과 대상 참여자가 없으면 리그 결과와 종목 평가만 저장한다")
    void noTargets() {
        Player withdrawn = player(LISTED_AT);
        final long stockId = stock(withdrawn);
        account(withdrawn, 0);
        withdrawn.withdraw(ENDS_AT.minusDays(2));
        entityManager.flush();

        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThat(result().getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(resultStocks()).extracting(LeagueResultStock::getStockId)
                .containsExactly(stockId);
        assertThat(resultPlayers()).isEmpty();
    }

    @Test
    @DisplayName("같은 리그의 결과가 이미 있으면 다시 산정하지 않는다")
    void rejectsDuplicate() {
        account(player(LISTED_AT), 100_000_000);
        entityManager.flush();
        useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT);

        assertThatThrownBy(() -> useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("리그 종료 트랜잭션 밖에서는 결과를 산정할 수 없다")
    void requiresTransaction() {
        assertThatThrownBy(() -> useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("결과 대상 참여자의 자산이 없으면 산정에 실패한다")
    void failsWhenAssetMissing() {
        account(player(LISTED_AT), 100_000_000);
        player(LISTED_AT);
        entityManager.flush();

        assertThatThrownBy(() -> useCase.calculate(LEAGUE_ID, ENDS_AT, FINALIZED_AT))
                .isInstanceOf(NaspiRuntimeException.class);
    }

    private Player player(LocalDateTime listedAt) {
        Player player = Player.enter(LEAGUE_ID, ++memberSeq, listedAt.minusHours(1));
        player.completeListing(listedAt);
        return entityManager.persist(player);
    }

    private long stock(Player lister) {
        Stock stock = Stock.list(LEAGUE_ID, lister.getId(), StockName.of("종목" + lister.getId()),
                StockDescription.of("소개"));
        stock.open();
        return entityManager.persistAndGetId(stock, Long.class);
    }

    private long stockLister(long stockId) {
        return entityManager.find(Stock.class, stockId).getListerPlayerId();
    }

    private void account(Player player, long cash) {
        entityManager.persist(Account.open(LEAGUE_ID, player.getId(), cash));
    }

    private void holding(Player player, long stockId, long quantity) {
        entityManager.persist(Holding.allocate(LEAGUE_ID, player.getId(), stockId, quantity));
    }

    private void trade(long stockId, int tradingDay, long price, long quantity,
            LocalDateTime executedAt) {
        Order sell = order(10L, stockId, OrderSide.SELL, price, quantity, tradingDay, executedAt);
        Order buy = order(20L, stockId, OrderSide.BUY, price, quantity, tradingDay, executedAt);
        entityManager.persist(Trade.record(new Execution(sell, buy, price, quantity), tradingDay,
                executedAt));
    }

    private Order order(long playerId, long stockId, OrderSide side, long price, long quantity,
            int tradingDay, LocalDateTime orderedAt) {
        Order order = Order.limit(LEAGUE_ID, playerId, stockId, side, price, quantity, tradingDay,
                orderedAt);
        ReflectionTestUtils.setField(order, "id", playerId);
        return order;
    }

    private LeagueResult result() {
        return entityManager.getEntityManager().createQuery(
                        "select r from LeagueResult r where r.leagueId = :l", LeagueResult.class)
                .setParameter("l", LEAGUE_ID).getSingleResult();
    }

    private List<LeagueResultStock> resultStocks() {
        return entityManager.getEntityManager().createQuery(
                        "select s from LeagueResultStock s where s.leagueId = :l "
                                + "order by s.stockId",
                        LeagueResultStock.class)
                .setParameter("l", LEAGUE_ID).getResultList();
    }

    /** 저장 순서(순위 순, 공동 순위는 나열 순)대로 조회한다. */
    private List<LeagueResultPlayer> resultPlayers() {
        return entityManager.getEntityManager().createQuery(
                        "select p from LeagueResultPlayer p where p.leagueId = :l order by p.id",
                        LeagueResultPlayer.class)
                .setParameter("l", LEAGUE_ID).getResultList();
    }

    private LeagueResultPlayer resultPlayer(Player player) {
        return resultPlayers().stream()
                .filter(result -> result.getPlayerId().equals(player.getId()))
                .findFirst().orElseThrow();
    }

    private List<DailyAssetSnapshot> lastDaySnapshots() {
        return entityManager.getEntityManager().createQuery(
                        "select d from DailyAssetSnapshot d where d.leagueId = :l "
                                + "and d.tradingDay = :t order by d.playerId",
                        DailyAssetSnapshot.class)
                .setParameter("l", LEAGUE_ID).setParameter("t", LAST_DAY).getResultList();
    }

    private List<LeagueResultHolding> holdingsOf(LeagueResultPlayer resultPlayer) {
        return entityManager.getEntityManager().createQuery(
                        "select h from LeagueResultHolding h where h.leagueResultPlayerId = :p "
                                + "order by h.stockId", LeagueResultHolding.class)
                .setParameter("p", resultPlayer.getId()).getResultList();
    }
}
