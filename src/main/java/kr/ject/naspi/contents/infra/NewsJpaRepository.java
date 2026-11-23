package kr.ject.naspi.contents.infra;

import jakarta.persistence.LockModeType;
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
    default List<News> findPublishedByStock(long leagueId, long stockId, Long cursor,
                                            int limit) {
        // 첫 페이지(커서 없음)는 모든 ID보다 큰 값을 커서로 쓴다 (DisclosureJpaRepository와 같은 이유)
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        return findByStatusBefore(leagueId, stockId, NewsStatus.PUBLISHED, before,
                Limit.of(limit));
    }

    @Query("""
            select n from News n
            where n.leagueId = :leagueId and n.stockId = :stockId and n.status = :status
              and n.id < :before
            order by n.id desc
            """)
    List<News> findByStatusBefore(@Param("leagueId") long leagueId,
                                  @Param("stockId") long stockId,
                                  @Param("status") NewsStatus status,
                                  @Param("before") long before, Limit limit);
}
