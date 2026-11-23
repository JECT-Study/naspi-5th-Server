package kr.ject.naspi.contents.application;

import java.util.List;

/**
 * 뉴스 댓글 목록 한 페이지 (최신순 커서 페이징).
 *
 * @param items 댓글 목록 (최신순)
 * @param nextCursor 다음 페이지를 요청할 때 보낼 커서. 더 없으면 null
 */
public record NewsCommentPage(List<NewsCommentItem> items, Long nextCursor) {
}
