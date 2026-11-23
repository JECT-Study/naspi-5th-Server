package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureTitle;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsGenerationResult;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueTradingDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({NewsRegenerationService.class, NewsRegenerationServiceTest.Config.class})
class NewsRegenerationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final long LEAGUE_ID = 3L;
    private static final long STOCK_ID = 11L;
    private static final long AUTHOR = 7L;
    private static final long AUTHOR_PLAYER = 70L;
    private static final long OTHER = 8L;
    private static final long OTHER_PLAYER = 80L;

    @Autowired
    private NewsRegenerationService service;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @BeforeEach
    void setUp() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID))
                .willReturn(Optional.of(new LeagueTradingDay(2, 7, NOW.plusDays(1),
                        NOW.minusDays(1), NOW.plusDays(6))));
        given(leagueQueryService.findPlayerId(AUTHOR, LEAGUE_ID))
                .willReturn(Optional.of(AUTHOR_PLAYER));
        given(leagueQueryService.findPlayerId(OTHER, LEAGUE_ID))
                .willReturn(Optional.of(OTHER_PLAYER));
    }

    @Test
    @DisplayName("공시 작성자는 실패한 뉴스를 다시 생성 요청할 수 있고 응답은 생성 중 상태다")
    void regenerate() {
        Disclosure disclosure = disclosure();
        News news = failedNews(disclosure);

        RegeneratedNews result = service.regenerate(AUTHOR, LEAGUE_ID, STOCK_ID,
                disclosure.getId());

        assertThat(result).isEqualTo(new RegeneratedNews(news.getId(), NewsStatus.PENDING));
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(news.getRegenerationRequestedAt()).isEqualTo(NOW);
        assertThat(events.stream(NewsRegenerationRequested.class)).containsExactly(
                new NewsRegenerationRequested(news.getId(), disclosure.getId()));
    }

    @Test
    @DisplayName("공시 작성자가 아니면 뉴스를 다시 생성할 수 없다")
    void notAuthor() {
        Disclosure disclosure = disclosure();
        failedNews(disclosure);

        assertBiz(() -> service.regenerate(OTHER, LEAGUE_ID, STOCK_ID, disclosure.getId()),
                ErrorCode.NEWS_REGENERATION_NOT_AUTHOR);
    }

    @Test
    @DisplayName("실패한 뉴스가 아니면 다시 생성할 수 없다")
    void notFailed() {
        Disclosure disclosure = disclosure();
        entityManager.persistAndFlush(News.requestFrom(disclosure, NOW));

        assertBiz(() -> service.regenerate(AUTHOR, LEAGUE_ID, STOCK_ID, disclosure.getId()),
                ErrorCode.NEWS_NOT_REGENERATABLE);
        assertThat(events.stream(NewsRegenerationRequested.class)).isEmpty();
    }

    @Test
    @DisplayName("삭제된 공시의 뉴스는 수동으로 다시 생성할 수 없다")
    void deletedDisclosure() {
        Disclosure disclosure = disclosure();
        failedNews(disclosure);
        disclosure.delete(AUTHOR_PLAYER);
        entityManager.flush();

        assertBiz(() -> service.regenerate(AUTHOR, LEAGUE_ID, STOCK_ID, disclosure.getId()),
                ErrorCode.DISCLOSURE_NOT_FOUND);
    }

    @Test
    @DisplayName("진행 중인 리그가 아니면 뉴스를 다시 생성할 수 없다")
    void leagueNotStarted() {
        Disclosure disclosure = disclosure();
        failedNews(disclosure);
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        assertBiz(() -> service.regenerate(AUTHOR, LEAGUE_ID, STOCK_ID, disclosure.getId()),
                ErrorCode.NEWS_REGENERATION_LEAGUE_NOT_STARTED);
    }

    @Test
    @DisplayName("재생성 요청 후 1분이 지나지 않으면 다시 요청할 수 없다 (정확히 1분이면 허용)")
    void tooSoon() {
        Disclosure disclosure = disclosure();
        News news = failedNews(disclosure);
        // 시계는 NOW 고정이므로 직전 요청 시각을 과거로 둔다
        ReflectionTestUtils.setField(news, "regenerationRequestedAt", NOW.minusSeconds(59));

        assertBiz(() -> service.regenerate(AUTHOR, LEAGUE_ID, STOCK_ID, disclosure.getId()),
                ErrorCode.NEWS_REGENERATION_TOO_SOON);

        ReflectionTestUtils.setField(news, "regenerationRequestedAt", NOW.minusSeconds(60));
        assertThat(service.regenerate(AUTHOR, LEAGUE_ID, STOCK_ID, disclosure.getId()).status())
                .isEqualTo(NewsStatus.PENDING);
    }

    private Disclosure disclosure() {
        return entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID, STOCK_ID,
                AUTHOR_PLAYER, DisclosureTitle.of("공시"), DisclosureBody.of("본문"), 2, NOW));
    }

    private News failedNews(Disclosure disclosure) {
        News news = entityManager.persistAndFlush(News.requestFrom(disclosure, NOW));
        news.complete(new NewsGenerationResult.Failed("오류"), NOW);
        entityManager.flush();
        return news;
    }

    private static void assertBiz(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        }
    }
}
