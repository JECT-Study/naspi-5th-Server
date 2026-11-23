package kr.ject.naspi.contents.infra;

/**
 * 대상별 댓글 수 조회 결과 (Spring Data 인터페이스 프로젝션).
 */
interface CommentCount {

    /**
     * 대상(공시·뉴스) ID.
     *
     * @return 대상 ID
     */
    Long getTargetId();

    /**
     * 삭제되지 않은 댓글 수.
     *
     * @return 댓글 수
     */
    Long getCommentCount();
}
