package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.ject.naspi.contents.domain.CommentContent;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureTitle;
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
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({NewsQueryService.class, NewsCommentService.class, NewsQueryServiceTest.Config.class})
class NewsQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final LocalDateTime PUBLISHED_AT = NOW.plusMinutes(1);
    private static final long LEAGUE_ID = 3L;
    private static final long OTHER_LEAGUE_ID = 4L;
    private static final long STOCK_ID = 11L;
    private static final long OTHER_STOCK_ID = 12L;
    private static final long VIEWER_MEMBER_ID = 8L;
    private static final long OUTSIDER_MEMBER_ID = 9L;
    private static final long VIEWER_PLAYER_ID = 80L;

    @Autowired
    private NewsQueryService queryService;

    @Autowired
    private TestEntityManager entityManager;

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
    }

    @Test
    @DisplayName("종목별 뉴스를 최신순으로 커서 페이징하고 호재/악재를 함께 준다")
    void cursorPaging() {
        long first = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);
        long second = published(LEAGUE_ID, STOCK_ID, NewsSentiment.NEGATIVE);
        long third = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);

        NewsPage page1 = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 2);
        assertThat(page1.items()).extracting(NewsListItem::newsId).containsExactly(third, second);
        assertThat(page1.items()).extracting(NewsListItem::sentiment)
                .containsExactly(NewsSentiment.POSITIVE, NewsSentiment.NEGATIVE);
        assertThat(page1.items()).extracting(NewsListItem::stockName)
                .containsOnly("종목A");
        assertThat(page1.nextCursor()).isNotNull();

        NewsPage page2 = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                page1.nextCursor(), 2);
        assertThat(page2.items()).extracting(NewsListItem::newsId).containsExactly(first);
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("종목별 뉴스 목록은 생성 순서가 아니라 발행 일시 최신순이고, 커서로 이어 받아도 빠지거나 겹치지 않는다")
    void publishedOrder() {
        // 먼저 생성된 뉴스가 재생성 등으로 더 늦게 발행됐다
        long latePublished = publishedAt(PUBLISHED_AT.plusMinutes(30));
        long early = publishedAt(PUBLISHED_AT);
        long middle = publishedAt(PUBLISHED_AT.plusMinutes(10));

        NewsPage page1 = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 2);
        assertThat(page1.items()).extracting(NewsListItem::newsId)
                .containsExactly(latePublished, middle);
        assertThat(page1.nextCursor()).isNotNull();

        NewsPage page2 = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                page1.nextCursor(), 2);
        assertThat(page2.items()).extracting(NewsListItem::newsId).containsExactly(early);
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("형식이 올바르지 않거나 공시 커서를 보내면 거절한다")
    void invalidCursor() {
        String disclosureCursor = new ContentCursor(NOW, FeedItemType.DISCLOSURE, 1L).encode();

        for (String cursor : new String[] {"not-a-cursor", "30", disclosureCursor}) {
            assertBiz(() -> queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                    cursor, 20), ErrorCode.INVALID_INPUT);
        }
    }

    @Test
    @DisplayName("발행된 뉴스만 목록·상세에 보인다")
    void onlyPublished() {
        long published = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);
        long pending = news(LEAGUE_ID, STOCK_ID, null);
        long skipped = news(LEAGUE_ID, STOCK_ID, new NewsGenerationResult.Skipped("없음"));
        long failed = news(LEAGUE_ID, STOCK_ID, new NewsGenerationResult.Failed("오류"));

        assertThat(queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20)
                .items()).extracting(NewsListItem::newsId).containsExactly(published);
        for (long id : new long[] {pending, skipped, failed}) {
            assertBiz(() -> queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id),
                    ErrorCode.NEWS_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("다른 종목·리그의 뉴스는 보이지 않는다")
    void otherStockOrLeague() {
        long otherStock = published(LEAGUE_ID, OTHER_STOCK_ID, NewsSentiment.POSITIVE);
        long otherLeague = published(OTHER_LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);

        assertThat(queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20)
                .items()).isEmpty();
        assertBiz(() -> queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, otherStock),
                ErrorCode.NEWS_NOT_FOUND);
        assertBiz(() -> queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, otherLeague),
                ErrorCode.NEWS_NOT_FOUND);
        assertBiz(() -> queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, 999_999L),
                ErrorCode.NEWS_NOT_FOUND);
    }

    @Test
    @DisplayName("공시가 삭제돼도 이미 발행된 뉴스는 목록·상세에서 조회할 수 있고, 삭제된 공시 원문은 상세에 담지 않는다")
    void deletedDisclosure() {
        long id = published(LEAGUE_ID, STOCK_ID, NewsSentiment.NEGATIVE);
        Long disclosureId = entityManager.find(News.class, id).getDisclosureId();
        entityManager.find(Disclosure.class, disclosureId).delete(70L);
        entityManager.flush();

        assertThat(queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20)
                .items()).extracting(NewsListItem::newsId).containsExactly(id);
        NewsDetail detail = queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id);
        assertThat(detail.newsId()).isEqualTo(id);
        assertThat(detail.disclosure()).isNull();
    }

    @Test
    @DisplayName("공시 없이 만든 뉴스는 상세의 원본 공시가 비어 있다")
    void newsWithoutDisclosure() {
        Disclosure source = entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID,
                STOCK_ID, 70L, DisclosureTitle.of("공시"), DisclosureBody.of("본문"), 2, NOW));
        News news = News.requestFrom(source, NOW);
        ReflectionTestUtils.setField(news, "disclosureId", null);
        news.complete(new NewsGenerationResult.Generated("뉴스 제목", "뉴스 본문",
                NewsSentiment.POSITIVE, "요약"), PUBLISHED_AT);
        long id = entityManager.persistAndFlush(news).getId();

        assertThat(queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id).disclosure())
                .isNull();
    }

    @Test
    @DisplayName("뉴스 상세는 목록 항목의 필드(제목, 본문, 호재/악재, 거래일·작성·발행 일시, 댓글 수)에 댓글 전체와 원본 공시를 더해 준다")
    void detail() {
        long id = published(LEAGUE_ID, STOCK_ID, NewsSentiment.NEGATIVE);
        long disclosureId = entityManager.find(News.class, id).getDisclosureId();
        long commentId = comment(id, VIEWER_PLAYER_ID, "댓글");

        NewsDetail detail = queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id);

        assertThat(detail).usingRecursiveComparison().ignoringFields("comments")
                .isEqualTo(new NewsDetail(id, STOCK_ID, "뉴스 제목", "뉴스 본문",
                        NewsSentiment.NEGATIVE, 2, NOW, PUBLISHED_AT, null,
                        new NewsDisclosure(disclosureId, "공시", "본문", NOW)));
        assertThat(detail.comments()).extracting(CommentItem::commentId)
                .containsExactly(commentId);
        assertThat(detail.comments().get(0).mine()).isTrue();
    }

    @Test
    @DisplayName("뉴스 목록은 뉴스마다 댓글 수와 가장 먼저 등록된 댓글을 함께 준다")
    void listWithComments() {
        long withComments = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);
        long withoutComments = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);
        final long first = comment(withComments, VIEWER_PLAYER_ID, "처음");
        comment(withComments, 70L, "나중");

        NewsPage page = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20);

        assertThat(page.items()).extracting(NewsListItem::newsId)
                .containsExactly(withoutComments, withComments);
        assertThat(page.items().get(0).comments()).isEqualTo(new CommentPreview(0, null));
        assertThat(page.items().get(1).comments().commentCount()).isEqualTo(2);
        assertThat(page.items().get(1).comments().previewComment().commentId()).isEqualTo(first);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 뉴스를 볼 수 없다")
    void outsider() {
        long id = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);

        assertBiz(() -> queryService.findByStock(OUTSIDER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null,
                20), ErrorCode.FORBIDDEN);
        assertBiz(() -> queryService.getDetail(OUTSIDER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id),
                ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("종료된 리그의 기록(뉴스)은 조회할 수 없다")
    void endedLeagueBlocked() {
        long id = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);
        given(leagueQueryService.requireAccessiblePlayer(VIEWER_MEMBER_ID, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_ENDED));

        assertBiz(() -> queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null,
                20), ErrorCode.LEAGUE_ALREADY_ENDED);
        assertBiz(() -> queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id),
                ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    private long published(long leagueId, long stockId, NewsSentiment sentiment) {
        return news(leagueId, stockId, new NewsGenerationResult.Generated("뉴스 제목", "뉴스 본문",
                sentiment, "요약"));
    }

    /** 종목에 발행 일시를 지정한 뉴스를 만든다. */
    private long publishedAt(LocalDateTime publishedAt) {
        Disclosure disclosure = entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID,
                STOCK_ID, 70L, DisclosureTitle.of("공시"), DisclosureBody.of("본문"), 2, NOW));
        News news = News.requestFrom(disclosure, NOW);
        news.complete(new NewsGenerationResult.Generated("뉴스 제목", "뉴스 본문",
                NewsSentiment.POSITIVE, "요약"), publishedAt);
        return entityManager.persistAndFlush(news).getId();
    }

    /** 공시와 뉴스를 만든다. result가 null이면 생성 중(PENDING)으로 둔다. */
    private long news(long leagueId, long stockId, NewsGenerationResult result) {
        Disclosure disclosure = entityManager.persistAndFlush(Disclosure.publish(leagueId,
                stockId, 70L, DisclosureTitle.of("공시"), DisclosureBody.of("본문"), 2, NOW));
        News news = entityManager.persistAndFlush(News.requestFrom(disclosure, NOW));
        if (result != null) {
            news.complete(result, PUBLISHED_AT);
            entityManager.flush();
        }
        return news.getId();
    }

    private long comment(long newsId, long playerId, String content) {
        News news = entityManager.find(News.class, newsId);
        return entityManager.persistAndFlush(NewsComment.write(news, playerId,
                CommentContent.of(content), NOW)).getId();
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
