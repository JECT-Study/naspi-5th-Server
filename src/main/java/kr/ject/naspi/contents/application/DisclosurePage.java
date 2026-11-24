package kr.ject.naspi.contents.application;

import java.util.List;

/**
 * 종목별 공시 목록 한 페이지 (최신순 커서 페이징).
 *
 * @param items 공시 목록 (최신순)
 * @param nextCursor 다음 페이지를 요청할 때 보낼 커서. 더 없으면 null
 */
public record DisclosurePage(List<DisclosureListItem> items, String nextCursor) {
}
