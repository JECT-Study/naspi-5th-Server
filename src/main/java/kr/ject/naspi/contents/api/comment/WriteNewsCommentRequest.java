package kr.ject.naspi.contents.api.comment;

import jakarta.validation.constraints.NotNull;

/**
 * 뉴스 댓글 작성 요청. 이미지·부모 댓글은 받지 않는다.
 *
 * @param content 댓글 내용 (1~100자, 공백만 불가. 길이·공백은 도메인에서 검증한다)
 */
record WriteNewsCommentRequest(@NotNull String content) {
}
