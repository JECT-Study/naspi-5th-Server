package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsGenerationResultTest {

    @Test
    @DisplayName("뉴스 제목은 최대 30자, 본문은 최대 100자까지 허용한다")
    void maxLength() {
        assertThatCode(() -> generated("가".repeat(30), "나".repeat(100)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> generated("가".repeat(31), "본문"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generated("제목", "나".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("뉴스 제목·본문은 최소 1자다")
    void blank() {
        assertThatThrownBy(() -> generated(" ", "본문"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generated("제목", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("호재·악재 구분과 공시 요약본이 없으면 생성 결과로 인정하지 않는다")
    void requiredFields() {
        assertThatThrownBy(() -> new NewsGenerationResult.Generated("제목", "본문", null, "요약"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewsGenerationResult.Generated(
                "제목", "본문", NewsSentiment.NEGATIVE, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NewsGenerationResult.Generated generated(String title, String body) {
        return new NewsGenerationResult.Generated(title, body, NewsSentiment.POSITIVE, "요약");
    }
}
