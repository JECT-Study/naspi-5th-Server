package kr.ject.naspi.contents.domain;

import java.util.List;

/**
 * 뉴스 생성 입력.
 *
 * @param title 공시 제목
 * @param body 공시 본문
 * @param imageUrls 공시 첨부 이미지 URL. AI 제공자가 내려받을 수 있는 주소여야 한다 (공개 URL 또는 data URL)
 * @param pastSummaries 같은 리그·같은 종목의 과거 공시 AI 요약본. 오래된 순. 첫 공시면 빈 목록
 */
public record NewsSource(String title, String body, List<String> imageUrls,
                         List<String> pastSummaries) {

    /**
     * 목록을 불변으로 복사한다.
     */
    public NewsSource {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        pastSummaries = pastSummaries == null ? List.of() : List.copyOf(pastSummaries);
    }
}
