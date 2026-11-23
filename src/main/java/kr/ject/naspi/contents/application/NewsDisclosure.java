package kr.ject.naspi.contents.application;

import java.time.LocalDateTime;
import kr.ject.naspi.contents.domain.Disclosure;

/**
 * 뉴스의 원본 공시. 공시 상세의 {@link DisclosureNews}와 짝을 이룬다.
 *
 * @param disclosureId 공시 ID
 * @param title 제목
 * @param body 본문
 * @param createdAt 작성 일시 (KST)
 */
public record NewsDisclosure(long disclosureId, String title, String body,
                             LocalDateTime createdAt) {

    static NewsDisclosure from(Disclosure disclosure) {
        return new NewsDisclosure(disclosure.getId(), disclosure.getTitle().getValue(),
                disclosure.getBody().getValue(), disclosure.getCreatedAt());
    }
}
