package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueRepository;
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

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueExpirationService.class, LeagueExpirationServiceTest.FixedClock.class})
class LeagueExpirationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 21, 0);

    @Autowired
    private LeagueExpirationService service;

    @Autowired
    private LeagueRepository leagueRepository;

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
    @DisplayName("생성 후 7일이 지나도록 시작하지 않은 대기실은 만료되고 초대 코드가 폐기된다")
    void expireOverdueWaitingRoom() {
        League overdue = persist("AAAAAAA", NOW.minusDays(7));

        int expired = service.expireOverdueWaitingRooms();

        assertThat(expired).isEqualTo(1);
        League found = entityManager.find(League.class, overdue.getId());
        assertThat(found.getStatus()).isEqualTo(LeagueStatus.EXPIRED);
        assertThat(found.getInviteCode()).isNull();
    }

    @Test
    @DisplayName("생성 후 7일이 지나지 않은 대기실은 만료되지 않는다")
    void keepRecentWaitingRoom() {
        League recent = persist("AAAAAAA", NOW.minusDays(7).plusSeconds(1));

        int expired = service.expireOverdueWaitingRooms();

        assertThat(expired).isZero();
        League found = entityManager.find(League.class, recent.getId());
        assertThat(found.getStatus()).isEqualTo(LeagueStatus.WAITING);
        assertThat(found.getInviteCode()).isEqualTo(InviteCode.of("AAAAAAA"));
    }

    @Test
    @DisplayName("7일 안에 시작된 리그는 만료되지 않는다")
    void keepStartedLeague() {
        League started = persist("AAAAAAA", NOW.minusDays(8));
        started.start(listedPlayers(started.getId()), NOW.minusDays(7).minusHours(1));
        entityManager.flush();

        int expired = service.expireOverdueWaitingRooms();

        assertThat(expired).isZero();
        assertThat(entityManager.find(League.class, started.getId()).getStatus())
                .isEqualTo(LeagueStatus.STARTED);
    }

    @Test
    @DisplayName("만료로 폐기된 초대 코드는 다른 리그에 다시 발급될 수 있다")
    void expiredCodeCanBeReissued() {
        persist("AAAAAAA", NOW.minusDays(7));

        service.expireOverdueWaitingRooms();
        entityManager.flush();

        assertThat(leagueRepository.existsByInviteCode(InviteCode.of("AAAAAAA"))).isFalse();
        League reissued = persist("AAAAAAA", NOW);
        assertThat(leagueRepository.findByInviteCode(InviteCode.of("AAAAAAA")))
                .contains(reissued);
    }

    /** 리그 시작 조건(상장을 마친 참여자 3명)을 만족하는 플레이어 목록. 저장하지 않는다. */
    private static List<Player> listedPlayers(long leagueId) {
        return LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(leagueId, memberId, NOW.minusDays(8));
                    player.completeListing(NOW.minusDays(8));
                    return player;
                })
                .toList();
    }

    private League persist(String code, LocalDateTime createdAt) {
        return entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of(code), createdAt));
    }
}
