package kr.ject.naspi.websocket.application.topic.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import kr.ject.naspi.contents.application.NewsGenerationCompleted;
import kr.ject.naspi.contents.application.NewsGenerationOutcome;
import kr.ject.naspi.contents.application.NewsGenerationType;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.websocket.api.response.alert.AlertResponse;
import kr.ject.naspi.websocket.api.response.alert.AlertType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlertMessageQueryTest {

    private final AlertMessageQuery query = new AlertMessageQuery(mock(StockQueryService.class));

    @Test
    @DisplayName("제보 기반 뉴스가 게시되면 '제보를 바탕으로 뉴스가 게시되었어요.' 알림을 만든다")
    void reportPublished() {
        assertThat(alert(NewsGenerationType.REPORT, NewsGenerationOutcome.PUBLISHED))
                .isEqualTo(new AlertResponse(AlertType.NEWS_PUBLISHED,
                        "제보를 바탕으로 뉴스가 게시되었어요.", null, "REPORT", 9L));
    }

    @Test
    @DisplayName("제보 내용이 부족해 미생성되면 두 줄 문구(줄바꿈 \\n)의 알림을 만든다")
    void reportSkipped() {
        assertThat(alert(NewsGenerationType.REPORT, NewsGenerationOutcome.SKIPPED))
                .isEqualTo(new AlertResponse(AlertType.NEWS_SKIPPED,
                        "제보 내용이 부족해 뉴스를 생성하지 못했어요.\n내용을 보완한 뒤 다시 시도해 주세요.",
                        null, "REPORT", 9L));
    }

    @Test
    @DisplayName("제보 기반 뉴스 생성이 실패하면 '뉴스를 생성하지 못했어요. 다시 시도해 주세요.' 알림을 만든다")
    void reportFailed() {
        assertThat(alert(NewsGenerationType.REPORT, NewsGenerationOutcome.FAILED))
                .isEqualTo(new AlertResponse(AlertType.NEWS_FAILED,
                        "뉴스를 생성하지 못했어요. 다시 시도해 주세요.", null, "REPORT", 9L));
    }

    @Test
    @DisplayName("공시 기반 뉴스가 게시되면 '공시를 바탕으로 뉴스가 게시되었어요.' 알림을 만든다")
    void disclosurePublished() {
        assertThat(alert(NewsGenerationType.DISCLOSURE, NewsGenerationOutcome.PUBLISHED))
                .isEqualTo(new AlertResponse(AlertType.NEWS_PUBLISHED,
                        "공시를 바탕으로 뉴스가 게시되었어요.", null, "DISCLOSURE", 9L));
    }

    @Test
    @DisplayName("공시 내용이 부족해 미생성되면 '공시 내용이 부족해 뉴스를 생성하지 못했어요.' 알림을 만든다")
    void disclosureSkipped() {
        assertThat(alert(NewsGenerationType.DISCLOSURE, NewsGenerationOutcome.SKIPPED))
                .isEqualTo(new AlertResponse(AlertType.NEWS_SKIPPED,
                        "공시 내용이 부족해 뉴스를 생성하지 못했어요.", null, "DISCLOSURE", 9L));
    }

    @Test
    @DisplayName("공시 기반 뉴스 생성이 실패하면 '뉴스를 생성하지 못했어요. 다시 시도해 주세요.' 알림을 만든다")
    void disclosureFailed() {
        assertThat(alert(NewsGenerationType.DISCLOSURE, NewsGenerationOutcome.FAILED))
                .isEqualTo(new AlertResponse(AlertType.NEWS_FAILED,
                        "뉴스를 생성하지 못했어요. 다시 시도해 주세요.", null, "DISCLOSURE", 9L));
    }

    private AlertResponse alert(NewsGenerationType type, NewsGenerationOutcome outcome) {
        return query.newsGenerationCompleted(new NewsGenerationCompleted(1L, 9L, 7L, type,
                outcome));
    }
}
