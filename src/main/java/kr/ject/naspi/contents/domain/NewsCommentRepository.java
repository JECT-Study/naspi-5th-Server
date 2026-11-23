package kr.ject.naspi.contents.domain;

/**
 * 뉴스 댓글 저장소. 구현은 infra의 {@code NewsCommentJpaRepository}가 맡는다.
 */
public interface NewsCommentRepository extends CommentRepository<NewsComment> {
}
