package kr.ject.naspi.contents.infra;

import java.util.List;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.contents.domain.DisclosureStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@link DisclosureRepository}의 Spring Data 구현.
 */
interface DisclosureJpaRepository extends Repository<Disclosure, Long>, DisclosureRepository {

    @Override
    default List<Disclosure> findPublishedByStock(long leagueId, long stockId, Long cursor,
                                                  int limit) {
        // 첫 페이지(커서 없음)는 모든 ID보다 큰 값을 커서로 쓴다. 쿼리에 null 파라미터 비교(:cursor is null)를
        // 두면 PostgreSQL 드라이버가 null의 타입을 추론하지 못해 실패할 수 있어서 조건을 id < :cursor 하나로 둔다.
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        return findByStatusBefore(leagueId, stockId, DisclosureStatus.PUBLISHED, before,
                Limit.of(limit));
    }

    @Override
    default List<String> findAiSummariesBefore(long leagueId, long stockId, long disclosureId) {
        return findAiSummaries(leagueId, stockId, disclosureId);
    }

    // 상태와 관계없이(삭제 포함) 요약이 있는 공시만, 작성 순(ID 오름차순)으로 찾는다
    @Query("""
            select d.aiSummary from Disclosure d
            where d.leagueId = :leagueId and d.stockId = :stockId and d.id < :before
              and d.aiSummary is not null
            order by d.id asc
            """)
    List<String> findAiSummaries(@Param("leagueId") long leagueId,
                                 @Param("stockId") long stockId,
                                 @Param("before") long before);

    // ID는 작성 순서대로 증가하므로 ID 내림차순이 최신순이다. 커서(마지막으로 받은 ID)보다 작은 것만 찾는다.
    @Query("""
            select d from Disclosure d
            where d.leagueId = :leagueId and d.stockId = :stockId and d.status = :status
              and d.id < :before
            order by d.id desc
            """)
    List<Disclosure> findByStatusBefore(@Param("leagueId") long leagueId,
                                        @Param("stockId") long stockId,
                                        @Param("status") DisclosureStatus status,
                                        @Param("before") long before, Limit limit);
}
