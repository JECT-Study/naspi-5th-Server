package kr.ject.naspi.contents.api.comment;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.application.CommentItem;

/**
 * 댓글 응답 (공시·뉴스 공통). 공시·뉴스 목록의 미리보기 댓글({@code previewComment})에도 쓴다.
 *
 * @param commentId 댓글 ID
 * @param content 내용
 * @param authorPlayerId 작성자 플레이어 ID
 * @param authorName 작성자 계정 이름 (탈퇴한 작성자는 "탈퇴한 사용자")
 * @param mine 내가 작성한 댓글인지 (삭제 버튼 노출)
 * @param createdAt 작성 일시 (KST)
 */
public record CommentResponse(long commentId, String content, long authorPlayerId,
                              String authorName, boolean mine, LocalDateTime createdAt) {

    /**
     * 댓글을 응답으로 바꾼다.
     *
     * @param c 댓글. null이면 null을 돌려준다 (댓글이 없는 대상의 가장 먼저 등록된 댓글)
     * @return 댓글 응답
     */
    public static CommentResponse from(CommentItem c) {
        return c == null ? null : new CommentResponse(c.commentId(), c.content(),
                c.authorPlayerId(), c.authorName(), c.mine(), c.createdAt());
    }
}
