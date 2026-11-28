package kr.ject.naspi.contents.domain;

import java.util.List;

/**
 * 제보 뉴스 생성 입력.
 *
 * @param stockName 대상 종목 이름
 * @param reporterName 제보자 이름
 * @param body 제보 본문
 * @param imageUrls 제보 첨부 이미지 URL, 최대 {@value #MAX_IMAGES}개. AI 제공자가 내려받을 수 있는 주소여야 한다
 */
public record ReportNewsSource(String stockName, String reporterName, String body,
                               List<String> imageUrls) {

    /** 제보 이미지 최대 개수. */
    public static final int MAX_IMAGES = 4;

    /**
     * 입력을 검증하고 이미지 목록을 불변으로 복사한다.
     *
     * @throws IllegalArgumentException 종목 이름·제보자 이름·본문이 비어 있거나 이미지가 최대 개수를 넘는 경우
     */
    public ReportNewsSource {
        requireText("종목 이름", stockName);
        requireText("제보자 이름", reporterName);
        requireText("제보 본문", body);
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        if (imageUrls.size() > MAX_IMAGES) {
            throw new IllegalArgumentException(
                    "제보 이미지는 최대 %d개입니다: %d개".formatted(MAX_IMAGES, imageUrls.size()));
        }
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "이 비어 있습니다");
        }
    }
}
