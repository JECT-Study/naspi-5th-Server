package kr.ject.naspi.league.infra;

import java.time.LocalDateTime;
import java.util.Collection;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@link PlayerRepository}의 Spring Data 구현.
 */
interface PlayerJpaRepository extends Repository<Player, Long>, PlayerRepository {

    @Override
    @Query("""
            select count(p) from Player p, League l
            where l.id = p.leagueId and p.memberId = :memberId and l.status in :statuses
              and (l.status <> kr.ject.naspi.league.domain.LeagueStatus.WAITING
                   or l.createdAt > :waitingCreatedAfter)
            """)
    long countLeaguesByMemberIdAndStatusIn(@Param("memberId") Long memberId,
            @Param("statuses") Collection<LeagueStatus> statuses,
            @Param("waitingCreatedAfter") LocalDateTime waitingCreatedAfter);
}
