package kr.ject.naspi.contents.infra;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ject.naspi.contents.domain.CommentStatus;
import kr.ject.naspi.contents.domain.DisclosureComment;
import kr.ject.naspi.contents.domain.DisclosureCommentRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@link DisclosureCommentRepository}의 Spring Data 구현. {@code NewsCommentJpaRepository}와 같은 쿼리를 쓴다.
 */
interface DisclosureCommentJpaRepository extends Repository<DisclosureComment, Long>,
        DisclosureCommentRepository {

    @Override
    default List<DisclosureComment> findActiveByTarget(long targetId) {
        return findByStatus(targetId, CommentStatus.ACTIVE);
    }

    @Override
    default Map<Long, Long> countActiveByTargets(Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        return countByStatus(targetIds, CommentStatus.ACTIVE).stream()
                .collect(Collectors.toMap(CommentCount::getTargetId,
                        CommentCount::getCommentCount));
    }

    @Override
    default List<DisclosureComment> findEarliestActiveByTargets(Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return List.of();
        }
        return findEarliestByStatus(targetIds, CommentStatus.ACTIVE);
    }

    @Query("""
            select c from DisclosureComment c
            where c.disclosureId = :disclosureId and c.status = :status
            order by c.id asc
            """)
    List<DisclosureComment> findByStatus(@Param("disclosureId") long disclosureId,
                                         @Param("status") CommentStatus status);

    @Query("""
            select c.disclosureId as targetId, count(c) as commentCount from DisclosureComment c
            where c.disclosureId in :disclosureIds and c.status = :status
            group by c.disclosureId
            """)
    List<CommentCount> countByStatus(@Param("disclosureIds") Collection<Long> disclosureIds,
                                     @Param("status") CommentStatus status);

    // ID는 작성 순서대로 증가하므로 대상별 가장 작은 ID가 가장 먼저 등록된 댓글이다
    @Query("""
            select c from DisclosureComment c
            where c.id in (
                select min(c2.id) from DisclosureComment c2
                where c2.disclosureId in :disclosureIds and c2.status = :status
                group by c2.disclosureId)
            """)
    List<DisclosureComment> findEarliestByStatus(
            @Param("disclosureIds") Collection<Long> disclosureIds,
            @Param("status") CommentStatus status);
}
