package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
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
    @DisplayName("자동 재시도까지 실패한 뉴스만 수동 재생성할 수 있고, 요청하면 생성 중으로 돌아간다")
    void regenerateFailedNews() {
        News news = failedNews();

        news.requestRegeneration(NOW);

        assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(news.getRegenerationRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("생성 중·발행·미생성 뉴스는 다시 생성할 수 없다")
    void regenerateRejectNotFailed() {
        News pending = News.requestFrom(disclosure(), NOW);
        News published = News.requestFrom(disclosure(), NOW);
        published.complete(new NewsGenerationResult.Generated("제목", "본문",
                NewsSentiment.POSITIVE, "요약"), NOW);
        News skipped = News.requestFrom(disclosure(), NOW);
        skipped.complete(new NewsGenerationResult.Skipped("없음"), NOW);

        for (News news : new News[] {pending, published, skipped}) {
            assertThatThrownBy(() -> news.requestRegeneration(NOW.plusHours(1)))
                    .isInstanceOfSatisfying(NaspiBizException.class, e -> assertThat(
                            e.getErrorCode()).isEqualTo(ErrorCode.NEWS_NOT_REGENERATABLE));
        }
    }

    @Test
    @DisplayName("재생성 요청은 뉴스당 1분에 한 번만 할 수 있다 (정확히 1분이 지나면 허용)")
    void regenerateInterval() {
        News news = failedNews();
        news.requestRegeneration(NOW);
        news.complete(new NewsGenerationResult.Failed("x"), NOW);

        assertThatThrownBy(() -> news.requestRegeneration(NOW.plusSeconds(59)))
                .isInstanceOfSatisfying(NaspiBizException.class, e -> assertThat(
                        e.getErrorCode()).isEqualTo(ErrorCode.NEWS_REGENERATION_TOO_SOON));
        assertThat(news.getStatus()).isEqualTo(NewsStatus.FAILED);

        news.requestRegeneration(NOW.plusSeconds(60));
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
    }

    @Test
    @DisplayName("제보 뉴스는 REPORT 유형의 생성 중 뉴스로 만들고 공시 ID는 비워 둔다")
    void requestFromReport() {
        News news = News.requestFromReport(report(), NOW);

        assertThat(news.getType()).isEqualTo(NewsType.REPORT);
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(news.getReportId()).isEqualTo(9L);
        assertThat(news.getDisclosureId()).isNull();
        assertThat(news.getLeagueId()).isEqualTo(3L);
        assertThat(news.getStockId()).isEqualTo(11L);
        assertThat(news.getTradingDay()).isEqualTo(4);
        assertThat(news.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("제보 뉴스 생성 결과가 생성이면 제목·본문·호재/악재를 채워 발행한다")
    void completeReportNewsGenerated() {
        News news = News.requestFromReport(report(), NOW);

        news.complete(new ReportNewsGenerationResult.Generated("종목, 소식", "본문 제보자 철수",
                NewsSentiment.NEGATIVE), NOW.plusMinutes(1));

        assertThat(news.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(news.getTitle()).isEqualTo("종목, 소식");
        assertThat(news.getBody()).isEqualTo("본문 제보자 철수");
        assertThat(news.getSentiment()).isEqualTo(NewsSentiment.NEGATIVE);
        assertThat(news.getPublishedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("제보 뉴스 생성 결과가 미생성이면 SKIPPED, 실패면 FAILED로 두고 내용은 채우지 않는다")
    void completeReportNewsNotPublished() {
        News skipped = News.requestFromReport(report(), NOW);
        News failed = News.requestFromReport(report(), NOW);

        skipped.complete(new ReportNewsGenerationResult.Skipped(), NOW);
        failed.complete(new ReportNewsGenerationResult.Failed("x"), NOW);

        assertThat(skipped.getStatus()).isEqualTo(NewsStatus.SKIPPED);
        assertThat(failed.getStatus()).isEqualTo(NewsStatus.FAILED);
        assertThat(skipped.getTitle()).isNull();
        assertThat(failed.getBody()).isNull();
        assertThat(failed.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("생성 중이 아닌 제보 뉴스에는 결과를 다시 반영할 수 없다")
    void completeReportNewsOnce() {
        News news = News.requestFromReport(report(), NOW);
        news.complete(new ReportNewsGenerationResult.Skipped(), NOW);

        assertThatThrownBy(() -> news.complete(new ReportNewsGenerationResult.Failed("x"), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(news.getStatus()).isEqualTo(NewsStatus.SKIPPED);
    }

    private static Report report() {
        Report report = Report.submit(3L, 11L, 70L, ReportBody.of("제보 본문"), 4, NOW);
        // 저장된 제보로만 뉴스를 만든다
        ReflectionTestUtils.setField(report, "id", 9L);
        return report;
    }

    private static News failedNews() {
        News news = News.requestFrom(disclosure(), NOW);
        news.complete(new NewsGenerationResult.Failed("x"), NOW);
        return news;
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
