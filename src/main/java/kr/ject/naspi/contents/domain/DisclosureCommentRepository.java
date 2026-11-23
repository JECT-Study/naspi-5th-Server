package kr.ject.naspi.contents.domain;

/**
 * 공시 댓글 저장소. 구현은 infra의 {@code DisclosureCommentJpaRepository}가 맡는다.
 */
public interface DisclosureCommentRepository extends CommentRepository<DisclosureComment> {
}
