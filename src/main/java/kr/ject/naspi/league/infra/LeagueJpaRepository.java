package kr.ject.naspi.league.infra;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

/**
 * {@link LeagueRepository}의 Spring Data 구현.
 */
interface LeagueJpaRepository extends Repository<League, Long>, LeagueRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<League> findWithLockByInviteCode(InviteCode inviteCode);
}
