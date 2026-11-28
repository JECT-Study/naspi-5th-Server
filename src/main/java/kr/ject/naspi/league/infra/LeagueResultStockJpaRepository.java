package kr.ject.naspi.league.infra;

import kr.ject.naspi.league.domain.LeagueResultStock;
import kr.ject.naspi.league.domain.LeagueResultStockRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link LeagueResultStockRepository}의 Spring Data 구현.
 */
interface LeagueResultStockJpaRepository
        extends Repository<LeagueResultStock, Long>, LeagueResultStockRepository {
}
