package kr.ject.naspi.contents.domain;

/**
 * 제보 뉴스 생성 결과.
 *
 * <p>생성, 미생성, 실패를 구분한다. 미생성은 실패와 다른 정상 결과다.
 */
public sealed interface ReportNewsGenerationResult {

    /**
     * 제보 뉴스 생성 성공.
     *
     * <p>제목 1~30자, 본문 1~100자, 호재·악재 값이 있어야 한다. 규칙을 어기면 생성할 수 없고,
     * 호출한 쪽은 이를 실패로 보고 재시도한다.
     *
     * @param title 제목
     * @param body 본문
     * @param sentiment 호재·악재
     */
    record Generated(String title, String body, NewsSentiment sentiment)
            implements ReportNewsGenerationResult {

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
        }

        /**
         * 제보 기준으로 형식까지 검증해 생성 결과를 만든다.
         *
         * <p>제목은 {@code "{종목 이름}, "} 로 시작하고 그 뒤에 내용이 있어야 하며,
         * 본문은 {@code "제보자 {제보자 이름}"} 으로 끝나야 한다.
         *
         * @throws IllegalArgumentException 검증 규칙을 어긴 경우
         */
        public static Generated of(ReportNewsSource source, String title, String body,
                                   NewsSentiment sentiment) {
            Generated generated = new Generated(title, body, sentiment);
            String titlePrefix = source.stockName() + ", ";
            if (!title.startsWith(titlePrefix)
                    || title.substring(titlePrefix.length()).isBlank()) {
                throw new IllegalArgumentException(
                        "제목은 '%s'로 시작하고 내용이 있어야 합니다: %s".formatted(titlePrefix, title));
            }
            String citation = "제보자 " + source.reporterName();
            if (!body.endsWith(citation)) {
                throw new IllegalArgumentException(
                        "본문은 '%s'으로 끝나야 합니다: %s".formatted(citation, body));
            }
            return generated;
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
     * 제보에서 기사 소재를 파악할 수 없어 뉴스를 만들지 않음. 재시도하지 않는다.
     */
    record Skipped() implements ReportNewsGenerationResult {

        /** 미생성 사유. */
        public static final String REASON = "제보 내용 부족";

        /**
         * 미생성 사유를 반환한다.
         */
        public String reason() {
            return REASON;
        }
    }

    /**
     * 재시도 한도까지 모두 실패.
     *
     * @param reason 마지막 실패 원인 (로그·운영 확인용, 사용자 노출 대상 아님)
     */
    record Failed(String reason) implements ReportNewsGenerationResult {
    }
}
