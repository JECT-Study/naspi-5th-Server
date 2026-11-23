package kr.ject.naspi.contents.domain;

/**
 * 댓글 상태.
 */
public enum CommentStatus {

    /** 조회되는 댓글. */
    ACTIVE,

    /** 작성자가 삭제한 댓글. 목록에서 빠진다. */
    DELETED
}
