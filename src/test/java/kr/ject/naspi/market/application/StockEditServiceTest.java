package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueApplicationTestConfig;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({StockEditService.class, LeagueApplicationTestConfig.class,
        StockEditServiceTest.FixedClock.class})
class StockEditServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 21, 0);
    private static final long OWNER_MEMBER = 10L;
    private static final long OTHER_MEMBER = 11L;

    @Autowired
    private StockEditService service;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    private League league;
    private long ownerPlayerId;
    private long stockId;

    private long leagueId;

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            Instant instant = NOW.atZone(ClockConfig.KST).toInstant();
            return Clock.fixed(instant, ClockConfig.KST);
        }
    }

    @BeforeEach
    void setUp() {
        league = createLeague(NOW.minusDays(1), "AAAAAAA");
        leagueId = league.getId();
        ownerPlayerId = enter(league, OWNER_MEMBER).getId();
        stockId = save(leagueId, ownerPlayerId, "나스피", "원래 소개").getId();
    }

    @Test
    @DisplayName("상장자는 대기실에서 종목 이름과 소개를 수정할 수 있다")
    void editInLobby() {
        StockDetail detail = service.editInLobby(leagueId, OWNER_MEMBER, stockId, " 새이름 ", "새 소개");

        assertThat(detail).isEqualTo(new StockDetail(stockId, "새이름", "새 소개"));
        Stock stock = stockRepository.findById(stockId).orElseThrow();
        assertThat(stock.getName().getValue()).isEqualTo("새이름");
        assertThat(stock.getDescription().getValue()).isEqualTo("새 소개");
    }

    @Test
    @DisplayName("현재 이름 그대로 두고 소개만 바꿔 수정해도 이름 중복으로 막히지 않는다")
    void editInLobbyKeepingName() {
        StockDetail detail = service.editInLobby(leagueId, OWNER_MEMBER, stockId, "나스피", "새 소개");

        assertThat(detail.name()).isEqualTo("나스피");
        assertThat(detail.description()).isEqualTo("새 소개");
    }

    @Test
    @DisplayName("같은 리그의 다른 종목과 이름이 같게 수정할 수 없다")
    void editInLobbyWithDuplicatedName() {
        save(leagueId, enter(league, OTHER_MEMBER).getId(), "다른종목", "소개");

        assertThatThrownBy(() -> service.editInLobby(leagueId, OWNER_MEMBER, stockId, "다른종목", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 리그의 종목과는 이름이 같아도 수정할 수 있다")
    void editInLobbyWithSameNameInOtherLeague() {
        otherLeagueStock();

        StockDetail detail = service.editInLobby(leagueId, OWNER_MEMBER, stockId, "다른종목", "소개");

        assertThat(detail.name()).isEqualTo("다른종목");
    }

    @Test
    @DisplayName("대기실 수정에도 상장과 같은 이름·소개 규칙이 적용되고, 어긋나면 수정되지 않는다")
    void editInLobbyWithInvalidInput() {
        assertThatThrownBy(() -> service.editInLobby(leagueId, OWNER_MEMBER, stockId, "!!!", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_NAME);
        assertThatThrownBy(() -> service.editInLobby(leagueId, OWNER_MEMBER, stockId, "새이름", " "))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_DESCRIPTION);

        Stock stock = stockRepository.findById(stockId).orElseThrow();
        assertThat(stock.getName().getValue()).isEqualTo("나스피");
        assertThat(stock.getDescription().getValue()).isEqualTo("원래 소개");
    }

    @Test
    @DisplayName("상장자는 종목 소개만 수정할 수 있고 이름은 그대로다")
    void changeDescription() {
        StockDetail detail = service.changeDescription(leagueId, OWNER_MEMBER, stockId, "새 소개");

        assertThat(detail).isEqualTo(new StockDetail(stockId, "나스피", "새 소개"));
    }

    @Test
    @DisplayName("소개 수정에도 소개 규칙(1~50자)이 적용된다")
    void changeDescriptionWithInvalidDescription() {
        String tooLong = "가".repeat(51);

        assertThatThrownBy(
                () -> service.changeDescription(leagueId, OWNER_MEMBER, stockId, tooLong))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_DESCRIPTION);
    }

    @Test
    @DisplayName("본인이 상장하지 않은 종목은 수정할 수 없다")
    void onlyOwnerCanEdit() {
        enter(league, OTHER_MEMBER);
        assertThatThrownBy(() -> service.editInLobby(leagueId, OTHER_MEMBER, stockId, "새이름", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_OWNED);
        assertThatThrownBy(() -> service.changeDescription(leagueId, OTHER_MEMBER, stockId, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_OWNED);
    }

    @Test
    @DisplayName("없는 종목이거나 다른 리그의 종목이면 찾을 수 없다")
    void stockNotFound() {
        assertThatThrownBy(() -> service.changeDescription(leagueId, OWNER_MEMBER, 9999L, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_FOUND);
        long other = otherLeagueId();
        enter(entityManager.find(League.class, other), OWNER_MEMBER);
        assertThatThrownBy(
                () -> service.changeDescription(other, OWNER_MEMBER, stockId, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("리그 시작 후에는 종목 이름을 수정할 수 없다")
    void rejectNameEditAfterStart() {
        ReflectionTestUtils.setField(league, "status", LeagueStatus.STARTED);

        assertThatThrownBy(() -> service.editInLobby(leagueId, OWNER_MEMBER, stockId, "새이름", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_STARTED);
        assertThat(stockRepository.findById(stockId).orElseThrow().getName().getValue())
                .isEqualTo("나스피");
    }

    @Test
    @DisplayName("만료된 대기실에서는 종목을 수정할 수 없다")
    void rejectEditInExpiredLobby() {
        League expired = createLeague(NOW.minusDays(7), "BBBBBBB");
        long player = enter(expired, OWNER_MEMBER).getId();
        long expiredStock = save(expired.getId(), player, "만료종목", "소개").getId();

        assertThatThrownBy(() -> service.editInLobby(
                expired.getId(), OWNER_MEMBER, expiredStock, "새이름", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
    }

    @Test
    @DisplayName("종목 소개는 대기실에서 수정할 수 있다")
    void changeDescriptionInLobby() {
        assertThat(service.changeDescription(leagueId, OWNER_MEMBER, stockId, "대기실 소개")
                .description()).isEqualTo("대기실 소개");
    }

    @Test
    @DisplayName("종목 소개는 리그 시작 이후에도 수정할 수 있다")
    void changeDescriptionAfterStart() {
        ReflectionTestUtils.setField(league, "status", LeagueStatus.STARTED);
        ReflectionTestUtils.setField(league, "endsAt", NOW.plusDays(3));

        assertThat(service.changeDescription(leagueId, OWNER_MEMBER, stockId, "진행 중 소개")
                .description()).isEqualTo("진행 중 소개");
    }

    @Test
    @DisplayName("종료된 리그(종료 시각이 지난 진행 중 리그 포함)에서는 종목 소개를 수정할 수 없다")
    void rejectChangeDescriptionAfterEnd() {
        ReflectionTestUtils.setField(league, "status", LeagueStatus.STARTED);
        ReflectionTestUtils.setField(league, "endsAt", NOW);

        assertThatThrownBy(() -> service.changeDescription(
                leagueId, OWNER_MEMBER, stockId, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED);

        ReflectionTestUtils.setField(league, "status", LeagueStatus.ENDED);
        assertThatThrownBy(() -> service.changeDescription(
                leagueId, OWNER_MEMBER, stockId, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 종목을 수정할 수 없다")
    void rejectNotJoinedMember() {
        assertThatThrownBy(() -> service.editInLobby(leagueId, 99L, stockId, "새이름", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.changeDescription(leagueId, 99L, stockId, "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    private League createLeague(LocalDateTime createdAt, String inviteCode) {
        return entityManager.persistAndFlush(League.create(
                LeagueName.of("리그"), InviteCode.of(inviteCode), createdAt));
    }

    private Player enter(League target, long memberId) {
        return entityManager.persistAndFlush(Player.enter(target.getId(), memberId, NOW));
    }

    private long otherLeagueId() {
        return createLeague(NOW.minusDays(1), "CCCCCCC").getId();
    }

    private Stock otherLeagueStock() {
        League other = createLeague(NOW.minusDays(1), "DDDDDDD");
        return save(other.getId(), enter(other, 20L).getId(), "다른종목", "소개");
    }

    private Stock save(long leagueId, long playerId, String name, String description) {
        return stockRepository.save(Stock.list(leagueId, playerId, StockName.of(name),
                StockDescription.of(description)));
    }
}
