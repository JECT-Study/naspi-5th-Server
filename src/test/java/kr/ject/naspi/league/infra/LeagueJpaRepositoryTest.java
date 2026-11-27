package kr.ject.naspi.league.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
class LeagueJpaRepositoryTest {

    private static final InviteCode CODE = InviteCode.of("AAAAAAA");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 21, 0);

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("대기실 리그끼리는 같은 초대 코드를 가질 수 없다 (동시 요청도 DB 제약이 막는다)")
    void inviteCodeUniqueAmongWaitingLeagues() {
        leagueRepository.save(newLeague());

        assertThatThrownBy(() -> {
            leagueRepository.save(newLeague());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("초대가 마감된 리그의 코드는 다른 리그에 다시 발급할 수 있다")
    void closedInviteCodeCanBeReused() {
        League closed = leagueRepository.save(newLeague());
        closed.closeInvitation();
        entityManager.flush();

        League reused = leagueRepository.save(newLeague());
        entityManager.flush();

        assertThat(leagueRepository.findWithLockByInviteCode(CODE)).contains(reused);
        assertThat(entityManager.find(League.class, closed.getId()).getInviteCode()).isNull();
    }

    @Test
    @DisplayName("초대가 마감된 리그는 여러 개여도 된다 (비어 있는 코드는 서로 겹치지 않는다)")
    void manyClosedLeagues() {
        League first = leagueRepository.save(League.create(
                LeagueName.of("리그"), InviteCode.of("AAAAAAA"), NOW));
        League second = leagueRepository.save(League.create(
                LeagueName.of("리그"), InviteCode.of("BBBBBBB"), NOW));
        first.closeInvitation();
        second.closeInvitation();

        entityManager.flush();

        assertThat(leagueRepository.existsByInviteCode(CODE)).isFalse();
    }

    @Test
    @DisplayName("종료 시각이 지난(같은 시각 포함) 진행 중인 리그만 종료 대상으로 조회한다")
    void findIdsByStatusAndEndsAtLessThanEqual() {
        League due = startedLeague("AAAAAAA");
        League notYet = startedLeague("BBBBBBB");
        ReflectionTestUtils.setField(notYet, "endsAt", due.getEndsAt().plusSeconds(1));
        League waiting = leagueRepository.save(League.create(
                LeagueName.of("리그"), InviteCode.of("CCCCCCC"), NOW));
        entityManager.flush();

        assertThat(leagueRepository.findIdsByStatusAndEndsAtLessThanEqual(
                LeagueStatus.STARTED, due.getEndsAt()))
                .containsExactly(due.getId())
                .doesNotContain(notYet.getId(), waiting.getId());
        assertThat(leagueRepository.findIdsByStatusAndEndsAtLessThanEqual(
                LeagueStatus.STARTED, due.getEndsAt().minusSeconds(1))).isEmpty();
    }

    private League startedLeague(String code) {
        League league = leagueRepository.save(League.create(
                LeagueName.of("리그"), InviteCode.of(code), NOW));
        List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(league.getId(), memberId, NOW);
                    player.completeListing(NOW);
                    return player;
                })
                .toList();
        league.start(players, NOW.plusHours(1));
        return league;
    }

    private static League newLeague() {
        return League.create(LeagueName.of("리그"), CODE, NOW);
    }
}
