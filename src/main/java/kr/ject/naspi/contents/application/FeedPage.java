package kr.ject.naspi.contents.application;

import java.util.List;

/**
 * 리그 통합 피드 한 페이지 (등록 일시 최신순 커서 페이징).
 *
 * @param items 공시·뉴스 목록 (최신순)
 * @param nextCursor 다음 페이지를 요청할 때 보낼 커서. 더 없으면 null
 */
public record FeedPage(List<FeedItem> items, String nextCursor) {
}
