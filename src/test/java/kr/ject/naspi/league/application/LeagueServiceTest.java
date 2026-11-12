package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.StockListingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueService.class, LeagueCreationUseCase.class, InviteCodeIssuer.class,
        LeagueJoinLimit.class, StockListingService.class, LeagueServiceTest.FixedClock.class})
class LeagueServiceTest {

    private static final long HOST_MEMBER_ID = 1L;
    private static final long GUEST_MEMBER_ID = 2L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 21, 0);

    @Autowired
    private LeagueService service;

    @Autowired
    private LeagueCreationUseCase useCase;

    @Autowired
    private TestEntityManager entityManager;

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            Instant instant = NOW.atZone(ClockConfig.KST).toInstant();
            return Clock.fixed(instant, ClockConfig.KST);
        }
    }

    @Test
    @DisplayName("상장을 마친 대기자는 참여자가 되고, 상장일시가 기록된다 (방장 위임 순서 기준)")
    void completeListing() {
        CreatedLeague created = createLeague();
        JoinedLeague joined = service.join(GUEST_MEMBER_ID, created.inviteCode());

        service.completeListing(joined.playerId());

        Player guest = entityManager.find(Player.class, joined.playerId());
        assertThat(guest.isListed()).isTrue();
        assertThat(guest.getListedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다른 사용자는 초대 코드로 대기실에 대기자로 입장할 수 있다")
    void join() {
        CreatedLeague created = createLeague();

        JoinedLeague joined = service.join(GUEST_MEMBER_ID, created.inviteCode().toLowerCase());

        assertThat(joined.leagueId()).isEqualTo(created.leagueId());
        Player guest = entityManager.find(Player.class, joined.playerId());
        assertThat(guest.getLeagueId()).isEqualTo(created.leagueId());
        assertThat(guest.getMemberId()).isEqualTo(GUEST_MEMBER_ID);
        assertThat(guest.getJoinedAt()).isEqualTo(NOW);
        assertThat(guest.isListed()).isFalse();
        assertThat(guest.getListedAt()).isNull();
    }

    @Test
    @DisplayName("없는 초대 코드로는 입장할 수 없다")
    void joinWithUnknownCode() {
        createLeague();

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, "ZZZZZZZ"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 시작된 리그에는 초대 코드로 입장할 수 없다")
    void joinStartedLeague() {
        CreatedLeague created = createLeague();
        League league = entityManager.find(League.class, created.leagueId());
        league.start(listedPlayers(created.leagueId()), NOW);
        entityManager.flush();

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("초대가 마감되어 코드가 비워진 리그에는 입장할 수 없다")
    void joinClosedInvitation() {
        CreatedLeague created = createLeague();
        entityManager.find(League.class, created.leagueId()).closeInvitation();
        entityManager.flush();

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("생성 후 7일이 지난 대기실의 초대 코드로는 입장할 수 없다 (만료 처리 전이어도)")
    void joinExpiredWaitingRoom() {
        League overdue = entityManager.persistAndFlush(League.create(LeagueName.of("리그"),
                InviteCode.of("AAAAAAA"), NOW.minusDays(7)));

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, "AAAAAAA"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
        assertThat(overdue.getStatus()).isEqualTo(LeagueStatus.WAITING);
    }

    @Test
    @DisplayName("이미 입장한 리그에는 다시 입장할 수 없다")
    void joinTwice() {
        CreatedLeague created = createLeague();
        service.join(GUEST_MEMBER_ID, created.inviteCode());

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_JOINED);
    }

    @Test
    @DisplayName("방장은 자신이 만든 리그에 초대 코드로 다시 입장할 수 없다")
    void hostCannotJoinOwnLeague() {
        CreatedLeague created = createLeague();

        assertThatThrownBy(() -> service.join(HOST_MEMBER_ID, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_JOINED);
    }

    @Test
    @DisplayName("참여자 + 대기자가 10명이 될 때까지 입장할 수 있다")
    void joinUpToMaxPlayers() {
        CreatedLeague created = createLeague();

        for (long memberId = 2; memberId <= League.MAX_PLAYERS; memberId++) {
            service.join(memberId, created.inviteCode());
        }

        assertThat(entityManager.getEntityManager()
                .createQuery("select count(p) from Player p where p.leagueId = :id", Long.class)
                .setParameter("id", created.leagueId())
                .getSingleResult()).isEqualTo(League.MAX_PLAYERS);
    }

    @Test
    @DisplayName("참여자 + 대기자가 10명이면 추가로 입장할 수 없다")
    void joinFullLeague() {
        CreatedLeague created = createLeague();
        for (long memberId = 2; memberId <= League.MAX_PLAYERS; memberId++) {
            service.join(memberId, created.inviteCode());
        }

        assertThatThrownBy(() -> service.join(League.MAX_PLAYERS + 1L, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_FULL);
    }

    /** 리그 시작 조건(상장을 마친 참여자 3명)을 만족하는 플레이어 목록. 저장하지 않는다. */
    private static List<Player> listedPlayers(long leagueId) {
        return LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(leagueId, memberId, NOW);
                    player.completeListing(NOW);
                    return player;
                })
                .toList();
    }

    private CreatedLeague createLeague() {
        return useCase.create(HOST_MEMBER_ID, "리그", "나스피", "소개");
    }
}
