package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureTitle;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsGenerationResult;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@DataJpaTest
@Import(NewsQueryService.class)
class NewsQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final LocalDateTime PUBLISHED_AT = NOW.plusMinutes(1);
    private static final long LEAGUE_ID = 3L;
    private static final long OTHER_LEAGUE_ID = 4L;
    private static final long STOCK_ID = 11L;
    private static final long OTHER_STOCK_ID = 12L;
    private static final long VIEWER_MEMBER_ID = 8L;
    private static final long OUTSIDER_MEMBER_ID = 9L;

    @Autowired
    private NewsQueryService queryService;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @BeforeEach
    void setUp() {
        given(leagueQueryService.findPlayerId(VIEWER_MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.of(80L));
        given(leagueQueryService.findPlayerId(OUTSIDER_MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.empty());
    }

    @Test
    @DisplayName("종목별 뉴스를 최신순으로 커서 페이징하고 호재/악재를 함께 준다")
    void cursorPaging() {
        long first = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);
        long second = published(LEAGUE_ID, STOCK_ID, NewsSentiment.NEGATIVE);
        long third = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);

        NewsPage page1 = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 2);
        assertThat(page1.items()).extracting(NewsItem::newsId).containsExactly(third, second);
        assertThat(page1.items()).extracting(NewsItem::sentiment)
                .containsExactly(NewsSentiment.POSITIVE, NewsSentiment.NEGATIVE);
        assertThat(page1.nextCursor()).isEqualTo(second);

        NewsPage page2 = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                page1.nextCursor(), 2);
        assertThat(page2.items()).extracting(NewsItem::newsId).containsExactly(first);
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("발행된 뉴스만 목록·상세에 보인다")
    void onlyPublished() {
        long published = published(LEAGUE_ID, STOCK_ID, NewsSentiment.POSITIVE);
        long pending = news(LEAGUE_ID, STOCK_ID, null);
        long skipped = news(LEAGUE_ID, STOCK_ID, new NewsGenerationResult.Skipped("없음"));
        long failed = news(LEAGUE_ID, STOCK_ID, new NewsGenerationResult.Failed("오류"));

        assertThat(queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20)
                .items()).extracting(NewsItem::newsId).containsExactly(published);
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
    @DisplayName("공시가 삭제돼도 이미 발행된 뉴스는 목록·상세에서 조회할 수 있다")
    void deletedDisclosure() {
        long id = published(LEAGUE_ID, STOCK_ID, NewsSentiment.NEGATIVE);
        Long disclosureId = entityManager.find(News.class, id).getDisclosureId();
        entityManager.find(Disclosure.class, disclosureId).delete(70L);
        entityManager.flush();

        assertThat(queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20)
                .items()).extracting(NewsItem::newsId).containsExactly(id);
        assertThat(queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id).newsId())
                .isEqualTo(id);
    }

    @Test
    @DisplayName("뉴스 상세는 제목, 본문, 호재/악재, 거래일·작성·발행 일시, 연결된 공시 ID를 준다")
    void detail() {
        long id = published(LEAGUE_ID, STOCK_ID, NewsSentiment.NEGATIVE);
        Long disclosureId = entityManager.find(News.class, id).getDisclosureId();

        NewsItem item = queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id);

        assertThat(item).isEqualTo(new NewsItem(id, STOCK_ID, disclosureId, "뉴스 제목", "뉴스 본문",
                NewsSentiment.NEGATIVE, 2, NOW, PUBLISHED_AT));
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

    private long published(long leagueId, long stockId, NewsSentiment sentiment) {
        return news(leagueId, stockId, new NewsGenerationResult.Generated("뉴스 제목", "뉴스 본문",
                sentiment, "요약"));
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

    private static void assertBiz(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
