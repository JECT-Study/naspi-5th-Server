package kr.ject.naspi.league.infra;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    @Override
    @Query("""
            select p.leagueId from Player p, League l
            where l.id = p.leagueId and p.memberId = :memberId and l.status = :status
            """)
    List<Long> findLeagueIdsByMemberIdAndLeagueStatus(@Param("memberId") Long memberId,
            @Param("status") LeagueStatus status);

    @Override
    @Query("""
            select p from Player p, League l
            where l.id = p.leagueId and p.memberId = :memberId and l.status = :leagueStatus
            """)
    List<Player> findAllByMemberIdAndLeagueStatus(@Param("memberId") Long memberId,
            @Param("leagueStatus") LeagueStatus leagueStatus);

    @Override
    @Query("""
            select p from Player p, League l
            where l.id = p.leagueId and p.memberId = :memberId and l.status in :leagueStatuses
            """)
    List<Player> findAllByMemberIdAndLeagueStatusIn(@Param("memberId") Long memberId,
            @Param("leagueStatuses") Collection<LeagueStatus> leagueStatuses);
}
