package kr.ject.naspi.league.infra;

import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link PlayerRepository}의 Spring Data 구현.
 */
interface PlayerJpaRepository extends Repository<Player, Long>, PlayerRepository {
}
