package kr.ject.naspi.contents.domain;

import java.util.List;
import java.util.Optional;

/**
 * 뉴스 댓글 저장소. 구현은 infra의 {@code NewsCommentJpaRepository}가 맡는다.
 */
public interface NewsCommentRepository {

    /**
     * 댓글을 저장한다.
     *
     * @param comment 댓글
     * @return 저장된 댓글 (ID 발급됨)
     */
    NewsComment save(NewsComment comment);

    /**
     * 댓글을 ID로 찾는다.
     *
     * @param id 댓글 ID
     * @return 댓글 (삭제 여부와 관계없이)
     */
    Optional<NewsComment> findById(Long id);

    /**
     * 뉴스의 삭제되지 않은 댓글을 최신순(ID 내림차순)으로 찾는다.
     *
     * @param newsId 뉴스 ID
     * @param cursor 이 ID보다 작은 댓글만 찾는다. 첫 페이지면 null
     * @param limit 최대 개수
     * @return 댓글 목록
     */
    List<NewsComment> findActiveByNews(long newsId, Long cursor, int limit);
}
