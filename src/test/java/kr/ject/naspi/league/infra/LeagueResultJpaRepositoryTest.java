package kr.ject.naspi.league.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.ject.naspi.league.domain.LeagueResult;
import kr.ject.naspi.league.domain.LeagueResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
class LeagueResultJpaRepositoryTest {

    private static final LocalDateTime FINALIZED_AT = LocalDateTime.of(2026, 11, 22, 9, 0);

    @Autowired
    private LeagueResultRepository leagueResultRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("리그 결과는 리그당 하나만 저장된다")
    void onePerLeague() {
        leagueResultRepository.save(LeagueResult.of(1L, FINALIZED_AT));
        entityManager.flush();

        assertThat(leagueResultRepository.existsByLeagueId(1L)).isTrue();
        assertThat(leagueResultRepository.existsByLeagueId(2L)).isFalse();
        assertThatThrownBy(() -> {
            leagueResultRepository.save(LeagueResult.of(1L, FINALIZED_AT.plusSeconds(1)));
            entityManager.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class,
                org.hibernate.exception.ConstraintViolationException.class);
    }
}
