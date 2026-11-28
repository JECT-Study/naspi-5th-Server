package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@DataJpaTest
@Import(LeagueStockLookupService.class)
class LeagueStockLookupServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long PLAYER_ID = 10L;

    @Autowired
    private LeagueStockLookupService service;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @Test
    @DisplayName("리그 참여자는 같은 리그 종목의 이름과 소개를 종목 ID 순으로 조회하고, 본인 종목에는 mine이 표시된다")
    void findAll() {
        joined();
        long mine = list(1L, PLAYER_ID, "내종목", "내 소개");
        long other = list(1L, 11L, "남종목", "남 소개");

        assertThat(service.findAll(MEMBER_ID, 1L))
                .extracting(MemberStock::stockId, MemberStock::name, MemberStock::description,
                        MemberStock::listerName, MemberStock::mine)
                .containsExactly(
                        tuple(mine, "내종목", "내 소개", "나", true),
                        tuple(other, "남종목", "남 소개", "친구", false));
    }

    @Test
    @DisplayName("다른 리그의 종목은 조회되지 않는다")
    void excludesOtherLeagueStocks() {
        joined();
        list(2L, 20L, "다른리그", "소개");

        assertThat(service.findAll(MEMBER_ID, 1L)).isEmpty();
    }

    @Test
    @DisplayName("상장 전 대기자도 입장한 리그의 종목을 조회할 수 있고, 본인 종목은 없다")
    void waitingPlayerCanLookup() {
        joined();
        list(1L, 11L, "남종목", "남 소개");

        assertThat(service.findAll(MEMBER_ID, 1L))
                .extracting(MemberStock::mine)
                .containsExactly(false);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 종목을 조회할 수 없다")
    void outsiderCannotLookup() {
        notJoined();
        list(1L, 11L, "남종목", "남 소개");

        assertThatThrownBy(() -> service.findAll(MEMBER_ID, 1L))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("리그 참여자는 종목 하나의 이름과 소개를 조회하고, 본인 종목 여부가 표시된다")
    void findOne() {
        joined();
        long mine = list(1L, PLAYER_ID, "내종목", "내 소개");
        long other = list(1L, 11L, "남종목", "남 소개");

        assertThat(service.findOne(MEMBER_ID, 1L, mine))
                .isEqualTo(new MemberStock(mine, "내종목", "내 소개", "나", true, null));
        assertThat(service.findOne(MEMBER_ID, 1L, other).mine()).isFalse();
    }

    @Test
    @DisplayName("다른 리그의 종목 ID로는 개별 조회할 수 없다")
    void findOneOfOtherLeague() {
        joined();
        long otherLeagueStock = list(2L, 20L, "다른리그", "소개");

        assertThatThrownBy(() -> service.findOne(MEMBER_ID, 1L, otherLeagueStock))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STOCK_NOT_FOUND));
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 종목을 개별 조회할 수 없다")
    void outsiderCannotFindOne() {
        notJoined();
        long stockId = list(1L, 11L, "남종목", "남 소개");

        assertThatThrownBy(() -> service.findOne(MEMBER_ID, 1L, stockId))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("최초 거래일의 기준가는 시초가이고 상·하한가는 기준가 ±30%다")
    void quoteOnFirstTradingDay() {
        joined();
        long id = openedStock(1L, PLAYER_ID);
        entityManager.persist(StockDailyPrice.open(1L, id, 1, 10_000));

        StockQuote quote = service.findOne(MEMBER_ID, 1L, id).quote();

        assertThat(quote).isEqualTo(new StockQuote(10_000, 10_000, 13_000, 7_000, 0,
                new BigDecimal("0.0"), 0));
    }

    @Test
    @DisplayName("현재가는 최신 체결가이고 거래량은 체결 수량의 합계다")
    void quoteAfterExecution() {
        joined();
        long id = openedStock(1L, PLAYER_ID);
        StockDailyPrice day1 = entityManager.persist(StockDailyPrice.open(1L, id, 1, 10_000));
        entityManager.find(Stock.class, id).applyTrade(12_000);
        day1.addVolume(3);
        entityManager.find(Stock.class, id).applyTrade(12_500);
        day1.addVolume(2);

        StockQuote quote = service.findAll(MEMBER_ID, 1L).get(0).quote();

        assertThat(quote).isEqualTo(new StockQuote(12_500, 10_000, 13_000, 7_000, 2_500,
                new BigDecimal("25.0"), 5));
    }

    @Test
    @DisplayName("거래일이 바뀌면 직전 거래일 종가가 기준가가 되고 거래량은 0부터 다시 센다")
    void quoteAfterTradingDayChange() {
        joined();
        long id = openedStock(1L, PLAYER_ID);
        StockDailyPrice day1 = entityManager.persist(StockDailyPrice.open(1L, id, 1, 10_000));
        entityManager.find(Stock.class, id).applyTrade(12_500);
        day1.addVolume(5);
        day1.close(12_500);
        entityManager.persist(StockDailyPrice.open(1L, id, 2, 12_500));

        StockQuote quote = service.findOne(MEMBER_ID, 1L, id).quote();

        assertThat(quote).isEqualTo(new StockQuote(12_500, 12_500, 16_200, 8_800, 0,
                new BigDecimal("0.0"), 0));
    }

    @Test
    @DisplayName("거래일 전환 처리 전에는 직전 거래일 시세를 보여 준다")
    void quoteBeforeTradingDayChangeProcessed() {
        joined();
        long id = openedStock(1L, PLAYER_ID);
        StockDailyPrice day1 = entityManager.persist(StockDailyPrice.open(1L, id, 1, 10_000));
        entityManager.find(Stock.class, id).applyTrade(11_000);
        day1.addVolume(4);

        StockQuote quote = service.findOne(MEMBER_ID, 1L, id).quote();

        assertThat(quote.basePrice()).isEqualTo(10_000);
        assertThat(quote.volume()).isEqualTo(4);
    }

    @Test
    @DisplayName("대기실에서는 시세가 없다")
    void noQuoteInWaitingRoom() {
        joined();
        long id = list(1L, PLAYER_ID, "내종목", "내 소개");

        assertThat(service.findOne(MEMBER_ID, 1L, id).quote()).isNull();
    }

    @Test
    @DisplayName("거래일이 여러 개면 가장 최근 거래일의 시세를 보여 준다")
    void quoteOfLatestTradingDay() {
        joined();
        long id = openedStock(1L, PLAYER_ID);
        entityManager.persist(StockDailyPrice.open(1L, id, 1, 10_000));
        StockDailyPrice day2 = entityManager.persist(StockDailyPrice.open(1L, id, 2, 10_000));
        entityManager.find(Stock.class, id).applyTrade(9_000);
        day2.addVolume(7);

        StockQuote quote = service.findOne(MEMBER_ID, 1L, id).quote();

        assertThat(quote.currentPrice()).isEqualTo(9_000);
        assertThat(quote.changeRate()).isEqualTo(new BigDecimal("-10.0"));
        assertThat(quote.volume()).isEqualTo(7);
    }

    @Test
    @DisplayName("종료된 리그의 기록(종목)은 조회할 수 없다")
    void endedLeagueBlocked() {
        joined();
        long id = openedStock(1L, PLAYER_ID);
        given(leagueQueryService.requireAccessiblePlayer(MEMBER_ID, 1L))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_ENDED));

        assertThatThrownBy(() -> service.findAll(MEMBER_ID, 1L))
                .isInstanceOfSatisfying(NaspiBizException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
        assertThatThrownBy(() -> service.findOne(MEMBER_ID, 1L, id))
                .isInstanceOfSatisfying(NaspiBizException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
    }

    @Test
    @DisplayName("없는 리그의 종목은 리그 없음으로 거절된다")
    void leagueNotFound() {
        given(leagueQueryService.getPlayers(MEMBER_ID, 1L))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
        given(leagueQueryService.requireAccessiblePlayer(MEMBER_ID, 1L))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.findAll(MEMBER_ID, 1L))
                .isInstanceOfSatisfying(NaspiBizException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.LEAGUE_NOT_FOUND));
        assertThatThrownBy(() -> service.findOne(MEMBER_ID, 1L, 1L))
                .isInstanceOfSatisfying(NaspiBizException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.LEAGUE_NOT_FOUND));
    }

    @Test
    @DisplayName("리그 단위 조회는 조회자 없이 리그의 종목 이름·소개·상장자 이름·시세를 종목 ID 순으로 준다")
    void findAllInLeagueWithoutViewer() {
        given(leagueQueryService.getPlayerNames(eq(1L), anyCollection()))
                .willReturn(Map.of(PLAYER_ID, "나", 11L, "친구"));
        long first = list(1L, PLAYER_ID, "내종목", "내 소개");
        long second = list(1L, 11L, "남종목", "남 소개");
        list(2L, 20L, "다른리그", "소개");

        assertThat(service.findAllInLeague(1L)).containsExactly(
                new LeagueStockView(first, "내종목", "내 소개", "나", null),
                new LeagueStockView(second, "남종목", "남 소개", "친구", null));
    }

    @Test
    @DisplayName("리그 단위 조회 결과는 회원 조회 결과에서 본인 종목 여부만 뺀 것과 같다")
    void findAllInLeagueSameAsMemberView() {
        joined();
        given(leagueQueryService.getPlayerNames(eq(1L), anyCollection()))
                .willReturn(Map.of(PLAYER_ID, "나", 11L, "친구"));
        long mine = openedStock(1L, PLAYER_ID);
        long other = list(1L, 11L, "남종목", "남 소개");
        entityManager.find(Stock.class, other).open();
        StockDailyPrice day1 = entityManager.persist(StockDailyPrice.open(1L, mine, 1, 10_000));
        entityManager.persist(StockDailyPrice.open(1L, other, 1, 10_000));
        entityManager.find(Stock.class, mine).applyTrade(12_500);
        day1.addVolume(5);
        entityManager.flush();

        List<MemberStock> member = service.findAll(MEMBER_ID, 1L);
        List<LeagueStockView> view = service.findAllInLeague(1L);

        assertThat(view.get(0).quote()).isNotNull();
        assertThat(view).containsExactlyElementsOf(member.stream()
                .map(s -> new LeagueStockView(s.stockId(), s.name(), s.description(),
                        s.listerName(), s.quote()))
                .toList());
    }

    @Test
    @DisplayName("시작된 리그에서 탈퇴한 상장자의 종목은 상장자 이름이 '탈퇴한 사용자'다")
    void findAllInLeagueWithdrawnLister() {
        given(leagueQueryService.getPlayerNames(eq(1L), anyCollection()))
                .willReturn(Map.of(11L, "탈퇴한 사용자"));
        list(1L, 11L, "남종목", "남 소개");

        assertThat(service.findAllInLeague(1L))
                .extracting(LeagueStockView::listerName)
                .containsExactly("탈퇴한 사용자");
    }

    @Test
    @DisplayName("상장된 종목이 없거나 없는 리그면 빈 목록이다")
    void findAllInLeagueNoStocks() {
        assertThat(service.findAllInLeague(1L)).isEmpty();
        assertThat(service.findAllInLeague(999L)).isEmpty();
    }

    private long openedStock(long leagueId, long listerPlayerId) {
        long id = list(leagueId, listerPlayerId, "내종목", "내 소개");
        entityManager.find(Stock.class, id).open();
        entityManager.flush();
        return id;
    }

    private void joined() {
        given(leagueQueryService.getPlayers(MEMBER_ID, 1L)).willReturn(new LeaguePlayers(1L,
                "리그", "ABC1234", null, false, null,
                List.of(player(PLAYER_ID, "나"), player(11L, "친구"))));
        given(leagueQueryService.requireAccessiblePlayer(MEMBER_ID, 1L)).willReturn(PLAYER_ID);
    }

    private void notJoined() {
        given(leagueQueryService.requireAccessiblePlayer(MEMBER_ID, 1L))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));
        given(leagueQueryService.getPlayers(MEMBER_ID, 1L))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));
    }

    private static LeaguePlayer player(long playerId, String name) {
        return new LeaguePlayer(playerId, name, false, false, true,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private long list(long leagueId, long listerPlayerId, String name, String description) {
        Stock stock = stockRepository.save(Stock.list(leagueId, listerPlayerId,
                StockName.of(name), StockDescription.of(description)));
        entityManager.flush();
        return stock.getId();
    }
}
