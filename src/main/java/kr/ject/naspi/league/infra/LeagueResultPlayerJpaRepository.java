package kr.ject.naspi.league.infra;

import kr.ject.naspi.league.domain.LeagueResultPlayer;
import kr.ject.naspi.league.domain.LeagueResultPlayerRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link LeagueResultPlayerRepository}의 Spring Data 구현.
 */
interface LeagueResultPlayerJpaRepository
        extends Repository<LeagueResultPlayer, Long>, LeagueResultPlayerRepository {
}
