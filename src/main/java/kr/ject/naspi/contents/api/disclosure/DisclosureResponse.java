package kr.ject.naspi.contents.api.disclosure;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.application.PublishedDisclosure;

/**
 * 작성된 공시 응답.
 *
 * @param disclosureId 공시 ID
 * @param stockId 종목 ID
 * @param title 제목
 * @param body 본문
 * @param tradingDay 작성한 거래일 번호 (1~7)
 * @param createdAt 작성 일시 (KST)
 * @param imageIds 첨부 이미지 ID (첨부 순서대로, 없으면 빈 배열)
 */
record DisclosureResponse(long disclosureId, long stockId, String title, String body,
                          int tradingDay, LocalDateTime createdAt, List<Long> imageIds) {

    static DisclosureResponse from(PublishedDisclosure disclosure) {
        return new DisclosureResponse(disclosure.disclosureId(), disclosure.stockId(),
                disclosure.title(), disclosure.body(), disclosure.tradingDay(),
                disclosure.createdAt(), disclosure.imageIds());
    }
}
