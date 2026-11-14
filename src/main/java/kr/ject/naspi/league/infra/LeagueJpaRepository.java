package kr.ject.naspi.league.infra;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * {@link LeagueRepository}의 Spring Data 구현.
 */
interface LeagueJpaRepository extends Repository<League, Long>, LeagueRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<League> findWithLockById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<League> findWithLockByInviteCode(InviteCode inviteCode);

    @Override
    @Query("select l.id from League l where l.status = :status")
    List<Long> findIdsByStatus(LeagueStatus status);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<League> findAllWithLockByStatusAndCreatedAtLessThanEqual(LeagueStatus status,
            LocalDateTime createdAt);
}
