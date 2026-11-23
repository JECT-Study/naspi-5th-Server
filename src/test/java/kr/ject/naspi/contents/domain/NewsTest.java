package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NewsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);

    @Test
    @DisplayName("결과가 이미 반영된 뉴스에는 다시 결과를 반영할 수 없다 (발행된 뉴스는 바뀌지 않는다)")
    void completeOnce() {
        News news = News.requestFrom(disclosure(), NOW);
        news.complete(new NewsGenerationResult.Generated("제목", "본문", NewsSentiment.POSITIVE,
                "요약"), NOW);

        assertThatThrownBy(() -> news.complete(new NewsGenerationResult.Failed("x"), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(news.getTitle()).isEqualTo("제목");
        assertThat(news.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("뉴스는 삭제·수정할 수 없고 이미지 없이 발행한다")
    void noDeleteOrImage() {
        assertThat(News.class.getMethods())
                .extracting(Method::getName)
                .noneMatch(name -> name.startsWith("delete") || name.startsWith("set")
                        || name.toLowerCase().contains("image"));
    }

    private static Disclosure disclosure() {
        Disclosure disclosure = Disclosure.publish(3L, 11L, 70L, DisclosureTitle.of("제목"),
                DisclosureBody.of("본문"), 2, NOW);
        // 저장된 공시로만 뉴스를 만든다
        ReflectionTestUtils.setField(disclosure, "id", 1L);
        return disclosure;
    }
}
