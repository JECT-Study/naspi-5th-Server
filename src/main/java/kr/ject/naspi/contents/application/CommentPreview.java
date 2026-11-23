package kr.ject.naspi.contents.application;

/**
 * 목록·상세에 함께 보여 주는 댓글 미리보기.
 *
 * @param commentCount 삭제되지 않은 댓글 수
 * @param latestComment 가장 최근 댓글. 댓글이 없으면 null
 */
public record CommentPreview(long commentCount, CommentItem latestComment) {

    /** 댓글이 없는 대상의 미리보기. */
    static final CommentPreview EMPTY = new CommentPreview(0, null);
}
