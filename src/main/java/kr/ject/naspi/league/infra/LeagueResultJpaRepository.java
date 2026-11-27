package kr.ject.naspi.league.infra;

import kr.ject.naspi.league.domain.LeagueResult;
import kr.ject.naspi.league.domain.LeagueResultRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link LeagueResultRepository}의 Spring Data 구현.
 */
interface LeagueResultJpaRepository extends Repository<LeagueResult, Long>, LeagueResultRepository {
}
