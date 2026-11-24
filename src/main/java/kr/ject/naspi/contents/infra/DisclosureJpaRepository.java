package kr.ject.naspi.contents.infra;

import java.time.LocalDateTime;
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

    // 종목별 공시 목록: 게시 중인 공시를 ID 커서로 최신순 조회
    @Override
    default List<Disclosure> findPublishedByStock(long leagueId, long stockId, Long cursor,
                                                  int limit) {
        // 첫 페이지(커서 없음)는 모든 ID보다 큰 값을 커서로 쓴다. 쿼리에 null 파라미터 비교(:cursor is null)를
        // 두면 PostgreSQL 드라이버가 null의 타입을 추론하지 못해 실패할 수 있어서 조건을 id < :cursor 하나로 둔다.
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        return findByStatusBefore(leagueId, stockId, DisclosureStatus.PUBLISHED, before,
                Limit.of(limit));
    }

    // 통합 피드: 게시 중인 공시를 (작성 일시, ID) 커서로 최신순 조회. 종목은 선택
    @Override
    default List<Disclosure> findPublishedInLeague(long leagueId, Long stockId,
                                                   LocalDateTime before, long idBefore,
                                                   int limit) {
        // null 파라미터 비교를 쿼리에 두지 않도록 첫 페이지 여부와 종목 조건에 따라 쿼리를 나눈다
        DisclosureStatus status = DisclosureStatus.PUBLISHED;
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

    // AI 뉴스 생성 맥락: 기준 공시 이전 공시들의 AI 요약
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

    // 피드 첫 페이지 (리그 전체): 작성 일시 최신순, 같으면 ID 내림차순
    @Query("""
            select d from Disclosure d
            where d.leagueId = :leagueId and d.status = :status
            order by d.createdAt desc, d.id desc
            """)
    List<Disclosure> findInLeagueFirst(@Param("leagueId") long leagueId,
                                       @Param("status") DisclosureStatus status, Limit limit);

    // 피드 첫 페이지 (종목 지정): 정렬은 리그 전체와 같다
    @Query("""
            select d from Disclosure d
            where d.leagueId = :leagueId and d.stockId = :stockId and d.status = :status
            order by d.createdAt desc, d.id desc
            """)
    List<Disclosure> findInStockFirst(@Param("leagueId") long leagueId,
                                      @Param("stockId") long stockId,
                                      @Param("status") DisclosureStatus status, Limit limit);

    // 피드 다음 페이지 (리그 전체): 커서 (before, idBefore)보다 뒤에 오는 공시만
    @Query("""
            select d from Disclosure d
            where d.leagueId = :leagueId and d.status = :status
              and (d.createdAt < :before or (d.createdAt = :before and d.id < :idBefore))
            order by d.createdAt desc, d.id desc
            """)
    List<Disclosure> findInLeagueBefore(@Param("leagueId") long leagueId,
                                        @Param("status") DisclosureStatus status,
                                        @Param("before") LocalDateTime before,
                                        @Param("idBefore") long idBefore, Limit limit);

    // 피드 다음 페이지 (종목 지정): 조건은 리그 전체와 같다
    @Query("""
            select d from Disclosure d
            where d.leagueId = :leagueId and d.stockId = :stockId and d.status = :status
              and (d.createdAt < :before or (d.createdAt = :before and d.id < :idBefore))
            order by d.createdAt desc, d.id desc
            """)
    List<Disclosure> findInStockBefore(@Param("leagueId") long leagueId,
                                       @Param("stockId") long stockId,
                                       @Param("status") DisclosureStatus status,
                                       @Param("before") LocalDateTime before,
                                       @Param("idBefore") long idBefore, Limit limit);
}
