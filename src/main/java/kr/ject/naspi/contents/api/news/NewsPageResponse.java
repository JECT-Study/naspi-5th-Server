package kr.ject.naspi.contents.api.news;

import java.util.List;
import kr.ject.naspi.contents.application.NewsPage;

/**
 * 종목별 뉴스 목록 응답.
 *
 * @param items 뉴스 목록 (최신순)
 * @param nextCursor 다음 페이지 요청 시 {@code cursor}로 보낼 값. 더 없으면 null
 */
record NewsPageResponse(List<NewsResponse> items, Long nextCursor) {

    static NewsPageResponse from(NewsPage page) {
        return new NewsPageResponse(page.items().stream().map(NewsResponse::from).toList(),
                page.nextCursor());
    }
}
