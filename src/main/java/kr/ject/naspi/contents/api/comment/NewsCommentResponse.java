package kr.ject.naspi.contents.api.comment;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.application.NewsCommentItem;

/**
 * 뉴스 댓글 응답.
 *
 * @param commentId 댓글 ID
 * @param content 내용
 * @param authorPlayerId 작성자 플레이어 ID
 * @param authorName 작성자 계정 이름 (탈퇴한 작성자는 "탈퇴한 사용자")
 * @param mine 내가 작성한 댓글인지 (삭제 버튼 노출)
 * @param createdAt 작성 일시 (KST)
 */
record NewsCommentResponse(long commentId, String content, long authorPlayerId,
                           String authorName, boolean mine, LocalDateTime createdAt) {

    static NewsCommentResponse from(NewsCommentItem c) {
        return new NewsCommentResponse(c.commentId(), c.content(), c.authorPlayerId(),
                c.authorName(), c.mine(), c.createdAt());
    }
}
