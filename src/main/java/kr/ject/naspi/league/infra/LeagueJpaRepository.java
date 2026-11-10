package kr.ject.naspi.league.infra;

import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link LeagueRepository}의 Spring Data 구현.
 */
interface LeagueJpaRepository extends Repository<League, Long>, LeagueRepository {
}
