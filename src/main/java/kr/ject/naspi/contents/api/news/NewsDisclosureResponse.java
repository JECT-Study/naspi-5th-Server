package kr.ject.naspi.contents.api.news;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.application.NewsDisclosure;

/**
 * 뉴스의 원본 공시. 공시 상세의 {@code news}와 짝을 이룬다.
 *
 * @param disclosureId 공시 ID
 * @param title 제목
 * @param body 본문
 * @param createdAt 작성 일시 (KST)
 */
record NewsDisclosureResponse(long disclosureId, String title, String body,
                              LocalDateTime createdAt) {

    static NewsDisclosureResponse from(NewsDisclosure disclosure) {
        return disclosure == null ? null : new NewsDisclosureResponse(disclosure.disclosureId(),
                disclosure.title(), disclosure.body(), disclosure.createdAt());
    }
}
