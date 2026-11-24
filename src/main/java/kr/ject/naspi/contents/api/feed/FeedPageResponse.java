package kr.ject.naspi.contents.api.feed;

import java.util.List;
import kr.ject.naspi.contents.application.FeedPage;

/**
 * 리그 통합 피드 응답.
 *
 * @param items 공시·뉴스 목록 (등록 일시 최신순)
 * @param nextCursor 다음 페이지 요청 시 {@code cursor}로 보낼 값. 더 없으면 null
 */
record FeedPageResponse(List<FeedItemResponse> items, String nextCursor) {

    static FeedPageResponse from(FeedPage page) {
        return new FeedPageResponse(page.items().stream().map(FeedItemResponse::from).toList(),
                page.nextCursor());
    }
}
