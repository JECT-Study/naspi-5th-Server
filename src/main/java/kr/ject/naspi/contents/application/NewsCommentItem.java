package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;

/**
 * 뉴스 댓글.
 *
 * @param commentId 댓글 ID
 * @param content 내용
 * @param authorPlayerId 작성자 플레이어 ID
 * @param authorName 작성자 계정 이름 (탈퇴한 작성자는 "탈퇴한 사용자")
 * @param mine 내가 작성한 댓글인지
 * @param createdAt 작성 일시 (KST)
 */
public record NewsCommentItem(long commentId, String content, long authorPlayerId,
                              String authorName, boolean mine, LocalDateTime createdAt) {
}
