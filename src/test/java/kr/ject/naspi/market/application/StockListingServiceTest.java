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
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({StockListingService.class, LeagueApplicationTestConfig.class,
        StockListingServiceTest.FixedClock.class})
class StockListingServiceTest {

    private static final long LEAGUE_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 21, 0);

    @Autowired
    private StockListingService service;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private TestEntityManager entityManager;

    private int inviteSequence = 1;

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            Instant instant = NOW.atZone(ClockConfig.KST).toInstant();
            return Clock.fixed(instant, ClockConfig.KST);
        }
    }

    @Test
    @DisplayName("대기자는 종목 이름과 소개를 작성해 상장할 수 있고, 상장 이벤트가 발행된다")
    void list() {
        League league = waitingRoom(NOW.minusDays(1));
        Player player = enter(league, 10L);

        long stockId = service.list(league.getId(), 10L, " 나스피 ", "친구들과 함께하는 모의 투자");

        assertThat(stockRepository.existsByLeagueIdAndName(league.getId(), StockName.of("나스피")))
                .isTrue();
        assertThat(events.stream(StockListed.class))
                .containsExactly(new StockListed(stockId, league.getId(), player.getId()));
    }

    @Test
    @DisplayName("대기자가 상장을 마치면 참여자가 되고 상장일시가 기록된다")
    void listCompletesListing() {
        League league = waitingRoom(NOW.minusDays(1));
        Player player = enter(league, 10L);

        service.list(league.getId(), 10L, "나스피", "소개");

        Player found = entityManager.find(Player.class, player.getId());
        assertThat(found.isListed()).isTrue();
        assertThat(found.getListedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("상장 종목의 상장자는 회원 ID가 아니라 그 리그의 플레이어 ID다")
    void listWithPlayerIdNotMemberId() {
        League league = waitingRoom(NOW.minusDays(1));
        enter(league, 1L);
        Player player = enter(league, 777L);

        long stockId = service.list(league.getId(), 777L, "나스피", "소개");

        assertThat(player.getId()).isNotEqualTo(777L);
        assertThat(entityManager.find(Stock.class, stockId).isListedBy(player.getId())).isTrue();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 상장할 수 없다")
    void rejectNotJoinedMember() {
        League league = waitingRoom(NOW.minusDays(1));

        assertThatThrownBy(() -> service.list(league.getId(), 10L, "나스피", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(stockRepository.findAllByLeagueId(league.getId())).isEmpty();
    }

    @Test
    @DisplayName("리그 시작 이후에는 상장할 수 없다")
    void rejectStartedLeague() {
        League league = waitingRoom(NOW.minusDays(1));
        enter(league, 10L);
        ReflectionTestUtils.setField(league, "status", LeagueStatus.STARTED);

        assertThatThrownBy(() -> service.list(league.getId(), 10L, "나스피", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_STARTED);
        assertThat(stockRepository.findAllByLeagueId(league.getId())).isEmpty();
    }

    @Test
    @DisplayName("생성 후 7일이 지난 대기실에는 상장할 수 없다 (만료 처리 전이라도 요청 시점에 다시 검증)")
    void rejectExpiredWaitingRoom() {
        League justBefore = waitingRoom(NOW.minusDays(7).plusNanos(1));
        enter(justBefore, 10L);
        League expired = waitingRoom(NOW.minusDays(7));
        enter(expired, 11L);

        assertThat(service.list(justBefore.getId(), 10L, "나스피", "소개")).isPositive();
        assertThatThrownBy(() -> service.list(expired.getId(), 11L, "다른종목", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
        assertThat(expired.getStatus()).isEqualTo(LeagueStatus.WAITING);
    }

    @Test
    @DisplayName("삭제되었거나 없는 리그에는 상장할 수 없다")
    void rejectDeletedOrUnknownLeague() {
        League deleted = waitingRoom(NOW.minusDays(1));
        enter(deleted, 10L);
        ReflectionTestUtils.setField(deleted, "status", LeagueStatus.DELETED);

        assertThatThrownBy(() -> service.list(deleted.getId(), 10L, "나스피", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
        assertThatThrownBy(() -> service.list(999_999L, 10L, "나스피", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
    }

    @Test
    @DisplayName("상장 시점의 종목에는 현재가가 없다 (시초가는 리그 시작 때 적용된다)")
    void noCurrentPriceAtListing() {
        League league = waitingRoom(NOW.minusDays(1));
        enter(league, 10L);

        long stockId = service.list(league.getId(), 10L, "나스피", "소개");

        assertThat(entityManager.find(Stock.class, stockId).getCurrentPrice()).isNull();
    }

    @Test
    @DisplayName("한 사용자는 하나의 종목만 상장할 수 있다")
    void onlyOneStockPerLister() {
        League league = waitingRoom(NOW.minusDays(1));
        enter(league, 10L);
        service.list(league.getId(), 10L, "나스피", "소개");

        assertThatThrownBy(() -> service.list(league.getId(), 10L, "다른종목", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_ALREADY_LISTED);
    }

    @Test
    @DisplayName("같은 리그 안에서는 종목 이름이 중복될 수 없다")
    void duplicatedNameInSameLeague() {
        League league = waitingRoom(NOW.minusDays(1));
        enter(league, 10L);
        enter(league, 11L);
        service.list(league.getId(), 10L, "나스피", "소개");

        assertThatThrownBy(() -> service.list(league.getId(), 11L, " 나스피 ", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 리그에서는 같은 종목 이름을 사용할 수 있다")
    void sameNameInOtherLeague() {
        League league = waitingRoom(NOW.minusDays(1));
        League other = waitingRoom(NOW.minusDays(1));
        enter(league, 10L);
        enter(other, 20L);
        service.list(league.getId(), 10L, "나스피", "소개");

        long otherStockId = service.list(other.getId(), 20L, "나스피", "소개");

        assertThat(otherStockId).isPositive();
    }

    @Test
    @DisplayName("규칙에 어긋나면 상장도 참여자 전환도 되지 않고 이벤트도 발행되지 않는다")
    void invalidInput() {
        League league = waitingRoom(NOW.minusDays(1));
        Player player = enter(league, 10L);

        assertThatThrownBy(() -> service.list(league.getId(), 10L, "나스피!", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_NAME);
        assertThatThrownBy(() -> service.list(league.getId(), 10L, "나스피", " "))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_DESCRIPTION);

        assertThat(events.stream(StockListed.class)).isEmpty();
        assertThat(stockRepository.existsByListerPlayerId(player.getId())).isFalse();
        assertThat(entityManager.find(Player.class, player.getId()).isListed()).isFalse();
    }

    @Test
    @DisplayName("리그 생성 중 방장 종목은 플레이어 ID로 상장되고 참여자 전환은 하지 않는다")
    void listHostStock() {
        League league = waitingRoom(NOW.minusDays(1));
        Player host = enter(league, 10L);

        long stockId = service.listHostStock(league.getId(), host.getId(), "나스피", "소개");

        assertThat(entityManager.find(Stock.class, stockId).isListedBy(host.getId())).isTrue();
        assertThat(entityManager.find(Player.class, host.getId()).isListed()).isFalse();
        assertThat(events.stream(StockListed.class))
                .containsExactly(new StockListed(stockId, league.getId(), host.getId()));
    }

    private League waitingRoom(LocalDateTime createdAt) {
        return entityManager.persistAndFlush(League.create(LeagueName.of("리그"),
                InviteCode.of(String.format("T%06d", inviteSequence++)), createdAt));
    }

    private Player enter(League league, long memberId) {
        return entityManager.persistAndFlush(Player.enter(league.getId(), memberId, NOW));
    }

    @Test
    @DisplayName("퇴장한 플레이어의 종목만 삭제하고, 다른 리그의 같은 플레이어·같은 리그의 다른 플레이어 종목은 유지한다")
    void delist() {
        service.listHostStock(LEAGUE_ID, 10L, "나스피", "소개");
        service.listHostStock(LEAGUE_ID, 11L, "다른종목", "소개");
        service.listHostStock(2L, 12L, "다른리그", "소개");

        service.delist(LEAGUE_ID, 10L);
        entityManager.flush();
        entityManager.clear();

        assertThat(stockRepository.existsByListerPlayerId(10L)).isFalse();
        assertThat(stockRepository.existsByLeagueIdAndName(LEAGUE_ID, StockName.of("나스피")))
                .isFalse();
        assertThat(stockRepository.findAllByLeagueId(LEAGUE_ID)).hasSize(1);
        assertThat(stockRepository.findAllByLeagueId(2L)).hasSize(1);
    }

    @Test
    @DisplayName("상장 전 대기자가 퇴장해 삭제할 종목이 없어도 정상 처리된다")
    void delistWithoutStock() {
        service.delist(LEAGUE_ID, 99L);

        assertThat(stockRepository.findAllByLeagueId(LEAGUE_ID)).isEmpty();
    }

    @Test
    @DisplayName("종목을 삭제한 뒤 같은 이름을 같은 리그에서 다시 상장할 수 있다")
    void relistSameNameAfterDelist() {
        service.listHostStock(LEAGUE_ID, 10L, "나스피", "소개");
        service.delist(LEAGUE_ID, 10L);
        entityManager.flush();

        service.listHostStock(LEAGUE_ID, 11L, "나스피", "소개");

        assertThat(stockRepository.findAllByLeagueId(LEAGUE_ID)).hasSize(1);
    }
}
