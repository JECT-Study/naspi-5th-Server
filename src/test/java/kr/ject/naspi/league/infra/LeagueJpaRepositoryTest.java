package kr.ject.naspi.league.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

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

    private static League newLeague() {
        return League.create(LeagueName.of("리그"), CODE, NOW);
    }
}
