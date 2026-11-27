package kr.ject.naspi.league.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import kr.ject.naspi.league.domain.DailyAssetSnapshot;
import kr.ject.naspi.league.domain.DailyAssetSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
class DailyAssetSnapshotJpaRepositoryTest {

    @Autowired
    private DailyAssetSnapshotRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("리그·거래일별로 자산 집계를 조회한다")
    void findByLeagueAndDay() {
        repository.saveAll(List.of(
                DailyAssetSnapshot.of(1L, 1, 10L, 50_000_000, 30_000_000, 80_000_000),
                DailyAssetSnapshot.of(1L, 1, 20L, 100_000_000, 0, 100_000_000),
                DailyAssetSnapshot.of(1L, 2, 10L, 50_000_000, 33_000_000, 83_000_000),
                DailyAssetSnapshot.of(2L, 1, 30L, 90_000_000, 0, 90_000_000)));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.existsByLeagueIdAndTradingDay(1L, 1)).isTrue();
        assertThat(repository.existsByLeagueIdAndTradingDay(1L, 3)).isFalse();
        assertThat(repository.findAllByLeagueIdAndTradingDay(1L, 1))
                .extracting(DailyAssetSnapshot::getPlayerId, DailyAssetSnapshot::getTotalAsset)
                .containsExactlyInAnyOrder(
                        tuple(10L, 80_000_000L),
                        tuple(20L, 100_000_000L));
    }

    @Test
    @DisplayName("같은 리그·거래일·참여자의 자산 집계는 중복 저장할 수 없다")
    void uniquePerLeagueDayPlayer() {
        repository.saveAll(List.of(
                DailyAssetSnapshot.of(1L, 1, 10L, 50_000_000, 30_000_000, 80_000_000)));
        entityManager.flush();

        assertThatThrownBy(() -> {
            repository.saveAll(List.of(
                    DailyAssetSnapshot.of(1L, 1, 10L, 100_000_000, 0, 100_000_000)));
            entityManager.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class,
                org.hibernate.exception.ConstraintViolationException.class);
    }
}
