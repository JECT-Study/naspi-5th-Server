package kr.ject.naspi.league.infra;

import kr.ject.naspi.league.domain.LeagueResultHolding;
import kr.ject.naspi.league.domain.LeagueResultHoldingRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link LeagueResultHoldingRepository}의 Spring Data 구현.
 */
interface LeagueResultHoldingJpaRepository
        extends Repository<LeagueResultHolding, Long>, LeagueResultHoldingRepository {
}
