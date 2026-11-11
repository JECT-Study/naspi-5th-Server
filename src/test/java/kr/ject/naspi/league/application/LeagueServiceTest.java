package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
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
@Import({LeagueService.class, InviteCodeIssuer.class, LeagueServiceTest.FixedClock.class})
class LeagueServiceTest {

    private static final long HOST_MEMBER_ID = 1L;
    private static final long GUEST_MEMBER_ID = 2L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 21, 0);
    private static final String STOCK_NAME = "나스피";
    private static final String STOCK_DESCRIPTION = "소개";

    @Autowired
    private LeagueService service;

    @Autowired
    private ApplicationEvents events;

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
    @DisplayName("리그를 생성하면 대기실 상태의 리그와 공통 초대 코드 1개가 생기고, 생성한 회원이 방장이 된다")
    void create() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "우리끼리 주식왕",
                STOCK_NAME, STOCK_DESCRIPTION);

        League league = entityManager.find(League.class, created.leagueId());
        assertThat(league.getName().getValue()).isEqualTo("우리끼리 주식왕");
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.WAITING);
        assertThat(league.getInviteCode().getValue())
                .isEqualTo(created.inviteCode())
                .matches("^[A-Z0-9]{7}$");
        assertThat(league.getHostPlayerId()).isEqualTo(created.hostPlayerId());
        assertThat(league.getCreatedAt()).isEqualTo(NOW);

        Player host = entityManager.find(Player.class, created.hostPlayerId());
        assertThat(host.getLeagueId()).isEqualTo(created.leagueId());
        assertThat(host.getMemberId()).isEqualTo(HOST_MEMBER_ID);
    }

    @Test
    @DisplayName("리그를 생성하면 방장의 종목 상장을 위해 리그 생성 이벤트가 발행된다")
    void publishLeagueCreated() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그", STOCK_NAME,
                STOCK_DESCRIPTION);

        assertThat(events.stream(LeagueCreated.class)).containsExactly(new LeagueCreated(
                created.leagueId(), created.hostPlayerId(), STOCK_NAME, STOCK_DESCRIPTION));
    }

    @Test
    @DisplayName("리그 이름은 다른 리그와 중복될 수 있다")
    void duplicatedNameAllowed() {
        CreatedLeague first = service.create(HOST_MEMBER_ID, "같은 이름",
                STOCK_NAME, STOCK_DESCRIPTION);
        CreatedLeague second = service.create(GUEST_MEMBER_ID, "같은 이름",
                STOCK_NAME, STOCK_DESCRIPTION);

        assertThat(second.leagueId()).isNotEqualTo(first.leagueId());
    }

    @Test
    @DisplayName("리그 이름이 12자를 넘으면 리그를 생성할 수 없다")
    void createWithTooLongName() {
        assertThatThrownBy(() -> service.create(HOST_MEMBER_ID, "가".repeat(13),
                STOCK_NAME, STOCK_DESCRIPTION))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_LEAGUE_NAME);
    }

    @Test
    @DisplayName("상장을 마친 대기자는 참여자가 되고, 상장일시가 기록된다 (방장 위임 순서 기준)")
    void completeListing() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그",
                STOCK_NAME, STOCK_DESCRIPTION);
        JoinedLeague joined = service.join(GUEST_MEMBER_ID, created.inviteCode());

        service.completeListing(joined.playerId());

        Player guest = entityManager.find(Player.class, joined.playerId());
        assertThat(guest.isListed()).isTrue();
        assertThat(guest.getListedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다른 사용자는 초대 코드로 대기실에 대기자로 입장할 수 있다")
    void join() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그",
                STOCK_NAME, STOCK_DESCRIPTION);

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
        service.create(HOST_MEMBER_ID, "리그", STOCK_NAME, STOCK_DESCRIPTION);

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, "ZZZZZZZ"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 시작된 리그에는 초대 코드로 입장할 수 없다")
    void joinStartedLeague() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그",
                STOCK_NAME, STOCK_DESCRIPTION);
        League league = entityManager.find(League.class, created.leagueId());
        // TODO: 리그 시작(LEA-09) 구현 후 도메인 메서드로 바꾼다
        ReflectionTestUtils.setField(league, "status", LeagueStatus.STARTED);
        entityManager.flush();

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("초대가 마감되어 코드가 비워진 리그에는 입장할 수 없다")
    void joinClosedInvitation() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그",
                STOCK_NAME, STOCK_DESCRIPTION);
        entityManager.find(League.class, created.leagueId()).closeInvitation();
        entityManager.flush();

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 입장한 리그에는 다시 입장할 수 없다")
    void joinTwice() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그",
                STOCK_NAME, STOCK_DESCRIPTION);
        service.join(GUEST_MEMBER_ID, created.inviteCode());

        assertThatThrownBy(() -> service.join(GUEST_MEMBER_ID, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_JOINED);
    }

    @Test
    @DisplayName("방장은 자신이 만든 리그에 초대 코드로 다시 입장할 수 없다")
    void hostCannotJoinOwnLeague() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그",
                STOCK_NAME, STOCK_DESCRIPTION);

        assertThatThrownBy(() -> service.join(HOST_MEMBER_ID, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_JOINED);
    }

    @Test
    @DisplayName("참여자 + 대기자가 10명이 될 때까지 입장할 수 있다")
    void joinUpToMaxPlayers() {
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그",
                STOCK_NAME, STOCK_DESCRIPTION);

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
        CreatedLeague created = service.create(HOST_MEMBER_ID, "리그",
                STOCK_NAME, STOCK_DESCRIPTION);
        for (long memberId = 2; memberId <= League.MAX_PLAYERS; memberId++) {
            service.join(memberId, created.inviteCode());
        }

        assertThatThrownBy(() -> service.join(League.MAX_PLAYERS + 1L, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_FULL);
    }
}
