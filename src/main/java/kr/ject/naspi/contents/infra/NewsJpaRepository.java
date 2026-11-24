package kr.ject.naspi.contents.infra;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.contents.domain.NewsStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@link NewsRepository}의 Spring Data 구현.
 */
interface NewsJpaRepository extends Repository<News, Long>, NewsRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<News> findWithLockByDisclosureId(long disclosureId);

    @Override
    default List<News> findPublishedInLeague(long leagueId, Long stockId, LocalDateTime before,
                                             long idBefore, int limit) {
        // DisclosureJpaRepository#findPublishedInLeague와 같은 이유로 첫 페이지·종목 조건에 따라 쿼리를 나눈다
        NewsStatus status = NewsStatus.PUBLISHED;
        if (before == null) {
            return stockId == null
                    ? findInLeagueFirst(leagueId, status, Limit.of(limit))
                    : findInStockFirst(leagueId, stockId, status, Limit.of(limit));
        }
        return stockId == null
                ? findInLeagueBefore(leagueId, status, before, idBefore, Limit.of(limit))
                : findInStockBefore(leagueId, stockId, status, before, idBefore,
                        Limit.of(limit));
    }

    @Query("""
            select n from News n
            where n.leagueId = :leagueId and n.status = :status
            order by n.publishedAt desc, n.id desc
            """)
    List<News> findInLeagueFirst(@Param("leagueId") long leagueId,
                                 @Param("status") NewsStatus status, Limit limit);

    @Query("""
            select n from News n
            where n.leagueId = :leagueId and n.stockId = :stockId and n.status = :status
            order by n.publishedAt desc, n.id desc
            """)
    List<News> findInStockFirst(@Param("leagueId") long leagueId,
                                @Param("stockId") long stockId,
                                @Param("status") NewsStatus status, Limit limit);

    @Query("""
            select n from News n
            where n.leagueId = :leagueId and n.status = :status
              and (n.publishedAt < :before or (n.publishedAt = :before and n.id < :idBefore))
            order by n.publishedAt desc, n.id desc
            """)
    List<News> findInLeagueBefore(@Param("leagueId") long leagueId,
                                  @Param("status") NewsStatus status,
                                  @Param("before") LocalDateTime before,
                                  @Param("idBefore") long idBefore, Limit limit);

    @Query("""
            select n from News n
            where n.leagueId = :leagueId and n.stockId = :stockId and n.status = :status
              and (n.publishedAt < :before or (n.publishedAt = :before and n.id < :idBefore))
            order by n.publishedAt desc, n.id desc
            """)
    List<News> findInStockBefore(@Param("leagueId") long leagueId,
                                 @Param("stockId") long stockId,
                                 @Param("status") NewsStatus status,
                                 @Param("before") LocalDateTime before,
                                 @Param("idBefore") long idBefore, Limit limit);
}
