package kr.ject.naspi.contents.domain;

/**
 * 뉴스 생성 결과.
 *
 * <p>생성, 미생성, 실패를 구분한다. 미생성은 실패와 다른 정상 결과다 (docs/ai-news/README.md "3.2").
 */
public sealed interface NewsGenerationResult {

    /**
     * 뉴스 생성 성공.
     *
     * <p>생성 결과 검증 규칙 (docs/ai-news/README.md "6.1"): 제목 1~30자, 본문 1~100자, 호재·악재 값 존재,
     * 공시 요약본 존재. 규칙을 어기면 생성할 수 없고, 호출한 쪽은 이를 실패로 보고 재시도한다.
     *
     * @param title 제목
     * @param body 본문
     * @param sentiment 호재·악재
     * @param summary 이번 공시 원문에서 확인한 사실의 AI 요약본. 공시에 저장해 다음 뉴스 생성의 입력으로 쓴다
     */
    record Generated(String title, String body, NewsSentiment sentiment, String summary)
            implements NewsGenerationResult {

        /** 제목 최대 글자 수. */
        public static final int TITLE_MAX_LENGTH = 30;

        /** 본문 최대 글자 수. */
        public static final int BODY_MAX_LENGTH = 100;

        /**
         * 생성 결과를 검증한다.
         *
         * @throws IllegalArgumentException 검증 규칙을 어긴 경우
         */
        public Generated {
            requireLength("제목", title, TITLE_MAX_LENGTH);
            requireLength("본문", body, BODY_MAX_LENGTH);
            if (sentiment == null) {
                throw new IllegalArgumentException("호재·악재 구분이 없습니다");
            }
            if (summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("공시 요약본이 없습니다");
            }
        }

        private static void requireLength(String name, String value, int max) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + "이 비어 있습니다");
            }
            int length = value.codePointCount(0, value.length());
            if (length > max) {
                throw new IllegalArgumentException(
                        "%s이 %d자를 넘습니다: %d자".formatted(name, max, length));
            }
        }
    }

    /**
     * 공시에서 기사 소재로 쓸 사실을 찾지 못해 뉴스를 만들지 않음. 재시도하지 않는다.
     *
     * @param reason 미생성 사유
     */
    record Skipped(String reason) implements NewsGenerationResult {
    }

    /**
     * 재시도 한도까지 모두 실패. 공시 작성자에게 수동 재시도를 제공한다.
     *
     * @param reason 마지막 실패 원인 (로그·운영 확인용, 사용자 노출 대상 아님)
     */
    record Failed(String reason) implements NewsGenerationResult {
    }
}
