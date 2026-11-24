package kr.ject.naspi.contents.api.disclosure;

import java.util.List;
import kr.ject.naspi.contents.application.DisclosurePage;

/**
 * 종목별 공시 목록 응답.
 *
 * @param items 공시 목록 (최신순)
 * @param nextCursor 다음 페이지 요청 시 {@code cursor}로 보낼 값. 더 없으면 null
 */
record DisclosurePageResponse(List<DisclosureListItemResponse> items, Long nextCursor) {

    static DisclosurePageResponse from(DisclosurePage page) {
        return new DisclosurePageResponse(
                page.items().stream().map(DisclosureListItemResponse::from).toList(),
                page.nextCursor());
    }
}
