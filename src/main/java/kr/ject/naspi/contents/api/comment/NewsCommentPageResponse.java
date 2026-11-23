package kr.ject.naspi.contents.api.comment;

import java.util.List;
import kr.ject.naspi.contents.application.NewsCommentPage;

/**
 * 뉴스 댓글 목록 응답.
 *
 * @param items 댓글 목록 (최신순)
 * @param nextCursor 다음 페이지 요청 시 {@code cursor}로 보낼 값. 더 없으면 null
 */
record NewsCommentPageResponse(List<NewsCommentResponse> items, Long nextCursor) {

    static NewsCommentPageResponse from(NewsCommentPage page) {
        return new NewsCommentPageResponse(
                page.items().stream().map(NewsCommentResponse::from).toList(),
                page.nextCursor());
    }
}
