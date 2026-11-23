package kr.ject.naspi.contents.infra;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ject.naspi.contents.domain.CommentStatus;
import kr.ject.naspi.contents.domain.NewsComment;
import kr.ject.naspi.contents.domain.NewsCommentRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@link NewsCommentRepository}의 Spring Data 구현. {@code DisclosureCommentJpaRepository}와 같은 쿼리를 쓴다.
 */
interface NewsCommentJpaRepository extends Repository<NewsComment, Long>, NewsCommentRepository {

    @Override
    default List<NewsComment> findActiveByTarget(long targetId) {
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
    default List<NewsComment> findLatestActiveByTargets(Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return List.of();
        }
        return findLatestByStatus(targetIds, CommentStatus.ACTIVE);
    }

    @Query("""
            select c from NewsComment c
            where c.newsId = :newsId and c.status = :status
            order by c.id desc
            """)
    List<NewsComment> findByStatus(@Param("newsId") long newsId,
                                   @Param("status") CommentStatus status);

    @Query("""
            select c.newsId as targetId, count(c) as commentCount from NewsComment c
            where c.newsId in :newsIds and c.status = :status
            group by c.newsId
            """)
    List<CommentCount> countByStatus(@Param("newsIds") Collection<Long> newsIds,
                                     @Param("status") CommentStatus status);

    // ID는 작성 순서대로 증가하므로 대상별 가장 큰 ID가 가장 최근 댓글이다
    @Query("""
            select c from NewsComment c
            where c.id in (
                select max(c2.id) from NewsComment c2
                where c2.newsId in :newsIds and c2.status = :status
                group by c2.newsId)
            """)
    List<NewsComment> findLatestByStatus(@Param("newsIds") Collection<Long> newsIds,
                                         @Param("status") CommentStatus status);
}
