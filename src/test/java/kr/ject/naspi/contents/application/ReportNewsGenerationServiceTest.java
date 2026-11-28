package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.contents.domain.ImageFormat;
import kr.ject.naspi.contents.domain.ImageStorage;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.contents.domain.NewsType;
import kr.ject.naspi.contents.domain.Report;
import kr.ject.naspi.contents.domain.ReportBody;
import kr.ject.naspi.contents.domain.ReportImage;
import kr.ject.naspi.contents.domain.ReportNewsGenerationResult;
import kr.ject.naspi.contents.domain.ReportNewsGenerator;
import kr.ject.naspi.contents.domain.ReportNewsSource;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({ReportNewsGenerationService.class, ImageService.class,
        ReportNewsGenerationServiceTest.Config.class})
class ReportNewsGenerationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);

    private static final long LEAGUE_ID = 3L;
    private static final long STOCK_ID = 11L;
    private static final long REPORTER_PLAYER_ID = 80L;

    @Autowired
    private ReportNewsGenerationService service;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private ReportNewsGenerator reportNewsGenerator;

    @MockitoBean
    private ImageStorage imageStorage;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @MockitoBean
    private StockQueryService stockQueryService;

    @BeforeEach
    void setUp() {
        given(stockQueryService.findAllByLeagueId(LEAGUE_ID)).willReturn(List.of(
                new LeagueStock(STOCK_ID - 1, "다른 종목", "소개", 69L, null),
                new LeagueStock(STOCK_ID, "철수전자", "소개", 70L, null)));
        given(leagueQueryService.getPlayerNames(LEAGUE_ID, List.of(REPORTER_PLAYER_ID)))
                .willReturn(Map.of(REPORTER_PLAYER_ID, "영희"));
        given(imageStorage.signedUrl(anyString(), any()))
                .willAnswer(invocation -> "https://signed/" + invocation.getArgument(0));
    }

    @Test
    @DisplayName("제보 뉴스는 대상 종목 이름·제보자 이름·제보 본문·첨부 이미지로 생성하고 과거 요약은 쓰지 않는다")
    void source() {
        Report report = report("라면 세 그릇을 먹었습니다.");
        entityManager.persist(ReportImage.attach(LEAGUE_ID, report.getId(), 1, "key-1", "b.png",
                ImageFormat.PNG, 10, NOW));
        entityManager.persist(ReportImage.attach(LEAGUE_ID, report.getId(), 0, "key-0", "a.png",
                ImageFormat.PNG, 10, NOW));
        entityManager.flush();
        given(reportNewsGenerator.generate(any()))
                .willReturn(new ReportNewsGenerationResult.Skipped());

        service.generate(report.getId());

        ArgumentCaptor<ReportNewsSource> source = ArgumentCaptor.forClass(ReportNewsSource.class);
        then(reportNewsGenerator).should().generate(source.capture());
        assertThat(source.getValue()).isEqualTo(new ReportNewsSource("철수전자", "영희",
                "라면 세 그릇을 먹었습니다.",
                List.of("https://signed/key-0", "https://signed/key-1")));
    }

    @Test
    @DisplayName("생성되면 제보 뉴스를 발행한다")
    void published() {
        Report report = report("본문");
        given(reportNewsGenerator.generate(any())).willReturn(
                new ReportNewsGenerationResult.Generated("철수전자, 라면 소식",
                        "라면 소비 급증 제보자 영희", NewsSentiment.POSITIVE));

        service.generate(report.getId());

        News news = news(report.getId());
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(news.getType()).isEqualTo(NewsType.REPORT);
        assertThat(news.getTitle()).isEqualTo("철수전자, 라면 소식");
        assertThat(news.getBody()).isEqualTo("라면 소비 급증 제보자 영희");
        assertThat(news.getSentiment()).isEqualTo(NewsSentiment.POSITIVE);
        assertThat(news.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("제보 내용 부족으로 미생성되면 SKIPPED로 두고 실패와 구분한다")
    void skipped() {
        Report report = report("ㅋㅋ");
        given(reportNewsGenerator.generate(any()))
                .willReturn(new ReportNewsGenerationResult.Skipped());

        service.generate(report.getId());

        News news = news(report.getId());
        assertThat(news.getStatus()).isEqualTo(NewsStatus.SKIPPED);
        assertThat(news.getStatus()).isNotEqualTo(NewsStatus.FAILED);
        assertThat(news.getTitle()).isNull();
    }

    @Test
    @DisplayName("결과가 반영되면 제보자를 대상으로 결과(게시·미생성·실패)별 뉴스 생성 이벤트를 발행한다")
    void publishesCompletedEvent() {
        Report published = report("본문1");
        Report skipped = report("본문2");
        given(reportNewsGenerator.generate(any()))
                .willReturn(new ReportNewsGenerationResult.Generated("제목", "본문",
                        NewsSentiment.POSITIVE))
                .willReturn(new ReportNewsGenerationResult.Skipped())
                .willReturn(new ReportNewsGenerationResult.Failed("ai down"));
        Report failed = report("본문3");

        service.generate(published.getId());
        service.generate(skipped.getId());
        service.generate(failed.getId());

        assertThat(events.stream(NewsGenerationCompleted.class).toList()).containsExactly(
                new NewsGenerationCompleted(LEAGUE_ID, news(published.getId()).getId(),
                        REPORTER_PLAYER_ID, NewsGenerationType.REPORT,
                        NewsGenerationOutcome.PUBLISHED),
                new NewsGenerationCompleted(LEAGUE_ID, news(skipped.getId()).getId(),
                        REPORTER_PLAYER_ID, NewsGenerationType.REPORT,
                        NewsGenerationOutcome.SKIPPED),
                new NewsGenerationCompleted(LEAGUE_ID, news(failed.getId()).getId(),
                        REPORTER_PLAYER_ID, NewsGenerationType.REPORT,
                        NewsGenerationOutcome.FAILED));
    }

    @Test
    @DisplayName("제보자 탈퇴로 생성 실패 처리해도 제보자에게 실패 이벤트를 발행한다")
    void failedEventWhenReporterWithdrawn() {
        Report report = report("본문");
        given(leagueQueryService.isPlayerWithdrawn(LEAGUE_ID, REPORTER_PLAYER_ID))
                .willReturn(true);

        service.generate(report.getId());

        assertThat(events.stream(NewsGenerationCompleted.class).map(
                NewsGenerationCompleted::outcome).toList())
                .containsExactly(NewsGenerationOutcome.FAILED);
    }

    @Test
    @DisplayName("생성에 실패해도 제보 원문은 유지된다")
    void failedKeepsReport() {
        Report report = report("원문 그대로");
        given(reportNewsGenerator.generate(any()))
                .willReturn(new ReportNewsGenerationResult.Failed("ai down"));

        service.generate(report.getId());

        assertThat(news(report.getId()).getStatus()).isEqualTo(NewsStatus.FAILED);
        entityManager.clear();
        assertThat(entityManager.find(Report.class, report.getId()).getBody().getValue())
                .isEqualTo("원문 그대로");
    }

    @Test
    @DisplayName("이미지 URL 발급에 실패하면 생성기를 부르지 않고 생성 실패로 처리한다")
    void imageUrlFailure() {
        Report report = report("본문");
        entityManager.persistAndFlush(ReportImage.attach(LEAGUE_ID, report.getId(), 0, "key-0",
                "a.png", ImageFormat.PNG, 10, NOW));
        given(imageStorage.signedUrl(anyString(), any()))
                .willThrow(new IllegalStateException("sign failed"));

        service.generate(report.getId());

        then(reportNewsGenerator).should(never()).generate(any());
        assertThat(news(report.getId()).getStatus()).isEqualTo(NewsStatus.FAILED);
    }

    @Test
    @DisplayName("뉴스 생성 전에 제보자가 탈퇴했으면 생성기를 부르지 않고 생성 실패로 처리한다")
    void reporterWithdrawn() {
        Report report = report("본문");
        given(leagueQueryService.isPlayerWithdrawn(LEAGUE_ID, REPORTER_PLAYER_ID))
                .willReturn(true);

        service.generate(report.getId());

        then(reportNewsGenerator).should(never()).generate(any());
        assertThat(news(report.getId()).getStatus()).isEqualTo(NewsStatus.FAILED);
    }

    @Test
    @DisplayName("생성 중이 아닌 제보 뉴스는 다시 생성하지 않는다")
    void notPending() {
        Report report = report("본문");
        news(report.getId()).complete(new ReportNewsGenerationResult.Skipped(), NOW);
        entityManager.flush();

        service.generate(report.getId());

        then(reportNewsGenerator).shouldHaveNoInteractions();
        assertThat(news(report.getId()).getStatus()).isEqualTo(NewsStatus.SKIPPED);
    }

    /** 제보 작성과 같이 제보와 생성 중(PENDING) 뉴스를 함께 저장한다. */
    private Report report(String body) {
        Report report = entityManager.persistAndFlush(Report.submit(LEAGUE_ID, STOCK_ID,
                REPORTER_PLAYER_ID, ReportBody.of(body), 2, NOW));
        entityManager.persistAndFlush(News.requestFromReport(report, NOW));
        return report;
    }

    private News news(long reportId) {
        return entityManager.getEntityManager()
                .createQuery("select n from News n where n.reportId = :id", News.class)
                .setParameter("id", reportId)
                .getSingleResult();
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        }
    }
}
