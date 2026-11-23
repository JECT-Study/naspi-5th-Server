package kr.ject.naspi.contents.infra;

import java.util.List;
import kr.ject.naspi.contents.domain.CommentStatus;
import kr.ject.naspi.contents.domain.NewsComment;
import kr.ject.naspi.contents.domain.NewsCommentRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@link NewsCommentRepository}의 Spring Data 구현.
 */
interface NewsCommentJpaRepository extends Repository<NewsComment, Long>, NewsCommentRepository {

    @Override
    default List<NewsComment> findActiveByNews(long newsId, Long cursor, int limit) {
        // 첫 페이지(커서 없음)는 모든 ID보다 큰 값을 커서로 쓴다 (DisclosureJpaRepository와 같은 이유)
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        return findByStatusBefore(newsId, CommentStatus.ACTIVE, before, Limit.of(limit));
    }

    @Query("""
            select c from NewsComment c
            where c.newsId = :newsId and c.status = :status and c.id < :before
            order by c.id desc
            """)
    List<NewsComment> findByStatusBefore(@Param("newsId") long newsId,
                                         @Param("status") CommentStatus status,
                                         @Param("before") long before, Limit limit);
}
