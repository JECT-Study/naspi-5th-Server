package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import kr.ject.naspi.contents.domain.CommentContent;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureComment;
import kr.ject.naspi.contents.domain.DisclosureReaction;
import kr.ject.naspi.contents.domain.DisclosureReactionType;
import kr.ject.naspi.contents.domain.DisclosureTitle;
import kr.ject.naspi.contents.domain.Image;
import kr.ject.naspi.contents.domain.ImageFormat;
import kr.ject.naspi.contents.domain.ImageStorage;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsComment;
import kr.ject.naspi.contents.domain.NewsGenerationResult;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
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

@ActiveProfiles("test")
@DataJpaTest
@Import({FeedQueryService.class, DisclosureQueryService.class, NewsQueryService.class,
        ImageService.class, DisclosureReactionService.class, DisclosureCommentService.class,
        NewsCommentService.class, FeedQueryServiceTest.Config.class})
class FeedQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final long LEAGUE_ID = 3L;
    private static final long OTHER_LEAGUE_ID = 4L;
    private static final long STOCK_ID = 11L;
    private static final long OTHER_STOCK_ID = 12L;
    private static final long AUTHOR_PLAYER_ID = 70L;
    private static final long VIEWER_MEMBER_ID = 8L;
    private static final long VIEWER_PLAYER_ID = 80L;
    private static final long OUTSIDER_MEMBER_ID = 9L;

    @Autowired
    private FeedQueryService feedQueryService;

    @Autowired
    private DisclosureQueryService disclosureQueryService;

    @Autowired
    private NewsQueryService newsQueryService;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private ImageStorage imageStorage;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @MockitoBean
    private StockQueryService stockQueryService;

    @BeforeEach
    void setUp() {
        given(stockQueryService.findAllByLeagueId(LEAGUE_ID)).willReturn(List.of(
                new LeagueStock(STOCK_ID, "종목A", "소개", 70L, null),
                new LeagueStock(OTHER_STOCK_ID, "종목B", "소개", 71L, null)));
        given(leagueQueryService.requireAccessiblePlayer(VIEWER_MEMBER_ID, LEAGUE_ID))
                .willReturn(VIEWER_PLAYER_ID);
        given(leagueQueryService.requireAccessiblePlayer(OUTSIDER_MEMBER_ID, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));
        given(imageStorage.signedUrl(anyString(), any()))
                .willAnswer(invocation -> "https://signed/" + invocation.getArgument(0));
    }

    @Test
    @DisplayName("리그의 공시와 발행된 뉴스를 한 목록에 등록 일시(공시는 작성, 뉴스는 발행) 최신순으로 준다")
    void mergedLatestFirst() {
        Disclosure first = disclosure(LEAGUE_ID, STOCK_ID, NOW);
        Disclosure second = disclosure(LEAGUE_ID, OTHER_STOCK_ID, NOW.plusMinutes(1));
        // 첫 공시의 뉴스는 두 번째 공시보다 늦게 발행됐다
        long firstNews = publishedNews(first, NOW.plusMinutes(2));
        disclosure(OTHER_LEAGUE_ID, STOCK_ID, NOW.plusMinutes(3));

        FeedPage page = find(null, null, null, 20);

        assertThat(keys(page)).containsExactly(
                FeedItemType.NEWS + ":" + firstNews,
                FeedItemType.DISCLOSURE + ":" + second.getId(),
                FeedItemType.DISCLOSURE + ":" + first.getId());
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("등록 일시가 같으면 공시가 먼저, 같은 종류는 ID 내림차순이고, 커서 페이징으로 이어 받아도 빠지거나 겹치지 않는다")
    void cursorPaging() {
        Disclosure a = disclosure(LEAGUE_ID, STOCK_ID, NOW);
        Disclosure b = disclosure(LEAGUE_ID, STOCK_ID, NOW);
        long newsOfA = publishedNews(a, NOW);
        long newsOfB = publishedNews(b, NOW.plusMinutes(1));
        Disclosure c = disclosure(LEAGUE_ID, STOCK_ID, NOW.plusMinutes(1));
        List<String> all = keys(find(null, null, null, 50));

        List<String> paged = new ArrayList<>();
        String cursor = null;
        do {
            FeedPage page = find(null, null, cursor, 2);
            assertThat(page.items()).hasSizeLessThanOrEqualTo(2);
            paged.addAll(keys(page));
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(all).containsExactly(
                FeedItemType.DISCLOSURE + ":" + c.getId(),
                FeedItemType.NEWS + ":" + newsOfB,
                FeedItemType.DISCLOSURE + ":" + b.getId(),
                FeedItemType.DISCLOSURE + ":" + a.getId(),
                FeedItemType.NEWS + ":" + newsOfA);
        assertThat(paged).isEqualTo(all);
    }

    @Test
    @DisplayName("종목과 종류(공시·뉴스)로 걸러 조회할 수 있다")
    void filter() {
        Disclosure mine = disclosure(LEAGUE_ID, STOCK_ID, NOW);
        long mineNews = publishedNews(mine, NOW.plusMinutes(1));
        Disclosure other = disclosure(LEAGUE_ID, OTHER_STOCK_ID, NOW.plusMinutes(2));
        long otherNews = publishedNews(other, NOW.plusMinutes(3));

        assertThat(keys(find(STOCK_ID, null, null, 20))).containsExactly(
                FeedItemType.NEWS + ":" + mineNews,
                FeedItemType.DISCLOSURE + ":" + mine.getId());
        assertThat(keys(find(null, FeedItemType.DISCLOSURE, null, 20))).containsExactly(
                FeedItemType.DISCLOSURE + ":" + other.getId(),
                FeedItemType.DISCLOSURE + ":" + mine.getId());
        assertThat(keys(find(null, FeedItemType.NEWS, null, 20))).containsExactly(
                FeedItemType.NEWS + ":" + otherNews,
                FeedItemType.NEWS + ":" + mineNews);
        assertThat(keys(find(OTHER_STOCK_ID, FeedItemType.NEWS, null, 20)))
                .containsExactly(FeedItemType.NEWS + ":" + otherNews);
    }

    @Test
    @DisplayName("피드 항목마다 종목 ID와 종목 이름을 준다")
    void stockInfo() {
        Disclosure mine = disclosure(LEAGUE_ID, STOCK_ID, NOW);
        long otherNews = publishedNews(disclosure(LEAGUE_ID, OTHER_STOCK_ID, NOW.plusMinutes(1)),
                NOW.plusMinutes(2));

        FeedPage page = find(null, null, null, 20);

        assertThat(page.items()).hasSize(3);
        NewsListItem news = (NewsListItem) page.items().get(0);
        assertThat(news.newsId()).isEqualTo(otherNews);
        assertThat(news.stockId()).isEqualTo(OTHER_STOCK_ID);
        assertThat(news.stockName()).isEqualTo("종목B");
        DisclosureListItem disclosure = (DisclosureListItem) page.items().get(2);
        assertThat(disclosure.disclosureId()).isEqualTo(mine.getId());
        assertThat(disclosure.stockId()).isEqualTo(STOCK_ID);
        assertThat(disclosure.stockName()).isEqualTo("종목A");
    }

    @Test
    @DisplayName("피드 항목은 종목별 공시 목록·뉴스 목록의 항목과 같다")
    void sameAsStockLists() {
        Disclosure d = disclosure(LEAGUE_ID, STOCK_ID, NOW);
        long newsId = publishedNews(d, NOW.plusMinutes(1));
        entityManager.persist(Image.attach(LEAGUE_ID, d.getId(), 0, "key-0", "a.png",
                ImageFormat.PNG, 10, NOW));
        entityManager.persist(DisclosureReaction.of(LEAGUE_ID, d.getId(), VIEWER_PLAYER_ID,
                DisclosureReactionType.LIKE));
        entityManager.persist(DisclosureComment.write(d, AUTHOR_PLAYER_ID,
                CommentContent.of("공시 댓글"), NOW));
        entityManager.persist(NewsComment.write(entityManager.find(News.class, newsId),
                VIEWER_PLAYER_ID, CommentContent.of("뉴스 댓글"), NOW));
        entityManager.flush();

        FeedPage feed = find(null, null, null, 20);

        assertThat(feed.items().get(0)).isEqualTo(newsQueryService
                .findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20).items().get(0));
        assertThat(feed.items().get(1)).isEqualTo(disclosureQueryService
                .findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20).items().get(0));
    }

    @Test
    @DisplayName("삭제된 공시와 발행되지 않은 뉴스는 빠지고, 공시가 삭제돼도 발행된 뉴스는 보인다")
    void visibility() {
        Disclosure deleted = disclosure(LEAGUE_ID, STOCK_ID, NOW);
        final long newsOfDeleted = publishedNews(deleted, NOW.plusMinutes(1));
        deleted.delete(AUTHOR_PLAYER_ID);
        Disclosure pending = disclosure(LEAGUE_ID, STOCK_ID, NOW.plusMinutes(2));
        entityManager.persist(News.requestFrom(pending, NOW.plusMinutes(2)));
        Disclosure failed = disclosure(LEAGUE_ID, STOCK_ID, NOW.plusMinutes(3));
        News failedNews = News.requestFrom(failed, NOW.plusMinutes(3));
        failedNews.complete(new NewsGenerationResult.Failed("오류"), NOW.plusMinutes(4));
        entityManager.persist(failedNews);
        entityManager.flush();

        assertThat(keys(find(null, null, null, 20))).containsExactly(
                FeedItemType.DISCLOSURE + ":" + failed.getId(),
                FeedItemType.DISCLOSURE + ":" + pending.getId(),
                FeedItemType.NEWS + ":" + newsOfDeleted);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 피드를 볼 수 없다")
    void outsider() {
        assertBiz(() -> feedQueryService.find(OUTSIDER_MEMBER_ID, LEAGUE_ID, null, null, null,
                20), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("종료된 리그의 기록(피드)은 조회할 수 없다")
    void endedLeagueBlocked() {
        given(leagueQueryService.requireAccessiblePlayer(VIEWER_MEMBER_ID, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_ENDED));

        assertBiz(() -> find(null, null, null, 20), ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("형식이 올바르지 않은 커서는 거절한다")
    void invalidCursor() {
        assertBiz(() -> find(null, null, "not-a-cursor", 20), ErrorCode.INVALID_INPUT);
    }

    private FeedPage find(Long stockId, FeedItemType type, String cursor, int size) {
        return feedQueryService.find(VIEWER_MEMBER_ID, LEAGUE_ID, stockId, type, cursor, size);
    }

    private static List<String> keys(FeedPage page) {
        return page.items().stream()
                .map(item -> item.type() + ":" + switch (item) {
                    case DisclosureListItem disclosure -> disclosure.disclosureId();
                    case NewsListItem news -> news.newsId();
                })
                .toList();
    }

    private Disclosure disclosure(long leagueId, long stockId, LocalDateTime createdAt) {
        return entityManager.persistAndFlush(Disclosure.publish(leagueId, stockId,
                AUTHOR_PLAYER_ID, DisclosureTitle.of("공시"), DisclosureBody.of("본문"), 2,
                createdAt));
    }

    private long publishedNews(Disclosure disclosure, LocalDateTime publishedAt) {
        News news = News.requestFrom(disclosure, disclosure.getCreatedAt());
        news.complete(new NewsGenerationResult.Generated("뉴스 제목", "뉴스 본문",
                NewsSentiment.POSITIVE, "요약"), publishedAt);
        return entityManager.persistAndFlush(news).getId();
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
