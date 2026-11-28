package kr.ject.naspi.contents.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import kr.ject.naspi.contents.domain.NewsSentiment;
import org.springframework.core.io.ClassPathResource;

/**
 * AI 생성기가 공통으로 쓰는 리소스 읽기와 호재·악재 값 파싱.
 */
final class AiResources {

    private AiResources() {
    }

    /**
     * 클래스패스 리소스를 UTF-8 문자열로 읽는다.
     *
     * @throws UncheckedIOException 읽을 수 없는 경우
     */
    static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("리소스를 읽을 수 없습니다: " + path, e);
        }
    }

    /**
     * AI 응답의 호재·악재 문자열을 {@link NewsSentiment} 로 바꾼다.
     *
     * @throws IllegalArgumentException null 이거나 올바르지 않은 값인 경우
     */
    static NewsSentiment sentiment(String value) {
        try {
            return NewsSentiment.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("호재·악재 값이 올바르지 않습니다: " + value, e);
        }
    }
}
