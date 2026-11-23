package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import kr.ject.naspi.contents.domain.CommentContent;
import kr.ject.naspi.contents.domain.CommentStatus;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureTitle;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsComment;
import kr.ject.naspi.contents.domain.NewsGenerationResult;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
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

@ActiveProfiles("test")
@DataJpaTest
@Import({NewsCommentService.class, NewsCommentServiceTest.Config.class})
class NewsCommentServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final long LEAGUE_ID = 3L;
    private static final long STOCK_ID = 11L;
    private static final long OTHER_STOCK_ID = 12L;
    private static final long ME = 7L;
    private static final long ME_PLAYER = 70L;
    private static final long OTHER = 8L;
    private static final long OTHER_PLAYER = 80L;
    private static final long OUTSIDER = 9L;

    @Autowired
    private NewsCommentService service;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @BeforeEach
    void setUp() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID))
                .willReturn(Optional.of(new LeagueTradingDay(2, 7, NOW.plusDays(1),
                        NOW.minusDays(1), NOW.plusDays(6))));
        given(leagueQueryService.findPlayerId(ME, LEAGUE_ID)).willReturn(Optional.of(ME_PLAYER));
        given(leagueQueryService.findPlayerId(OTHER, LEAGUE_ID))
                .willReturn(Optional.of(OTHER_PLAYER));
        given(leagueQueryService.findPlayerId(OUTSIDER, LEAGUE_ID)).willReturn(Optional.empty());
        given(leagueQueryService.getPlayers(ME, LEAGUE_ID)).willReturn(players());
        given(leagueQueryService.getPlayers(OTHER, LEAGUE_ID)).willReturn(players());
    }

    @Test
    @DisplayName("리그 참여자는 종목과 관계없이 발행된 뉴스에 댓글을 쓸 수 있다")
    void write() {
        long news = publishedNews(STOCK_ID);

        NewsCommentItem item = service.write(ME, LEAGUE_ID, STOCK_ID, news, "첫 댓글");

        assertThat(item.content()).isEqualTo("첫 댓글");
        assertThat(item.authorPlayerId()).isEqualTo(ME_PLAYER);
        assertThat(item.authorName()).isEqualTo("나");
        assertThat(item.mine()).isTrue();
        assertThat(item.createdAt()).isEqualTo(NOW);
        assertThat(entityManager.find(NewsComment.class, item.commentId()).getNewsId())
                .isEqualTo(news);
    }

    @Test
    @DisplayName("진행 중이 아닌 리그에서는 댓글을 쓸 수 없다")
    void leagueNotStarted() {
        long news = publishedNews(STOCK_ID);
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        assertBiz(() -> service.write(ME, LEAGUE_ID, STOCK_ID, news, "댓글"),
                ErrorCode.COMMENT_LEAGUE_NOT_STARTED);
    }

    @Test
    @DisplayName("발행되지 않은 뉴스에는 댓글을 쓸 수 없다")
    void unpublishedNews() {
        long failed = news(STOCK_ID, new NewsGenerationResult.Failed("오류"));

        assertBiz(() -> service.write(ME, LEAGUE_ID, STOCK_ID, failed, "댓글"),
                ErrorCode.NEWS_NOT_FOUND);
    }

    @Test
    @DisplayName("댓글 목록은 최신순 커서 페이징이고 작성자 이름과 내 댓글 여부를 표시한다")
    void list() {
        long news = publishedNews(STOCK_ID);
        long first = comment(news, ME_PLAYER, "1");
        long second = comment(news, OTHER_PLAYER, "2");
        long third = comment(news, 99L, "3");

        NewsCommentPage page1 = service.findByNews(ME, LEAGUE_ID, STOCK_ID, news, null, 2);
        assertThat(page1.items()).extracting(NewsCommentItem::commentId)
                .containsExactly(third, second);
        assertThat(page1.items()).extracting(NewsCommentItem::authorName)
                .containsExactly("탈퇴한 사용자", "상대");
        assertThat(page1.items()).extracting(NewsCommentItem::mine).containsExactly(false, false);
        assertThat(page1.nextCursor()).isEqualTo(second);

        NewsCommentPage page2 = service.findByNews(ME, LEAGUE_ID, STOCK_ID, news,
                page1.nextCursor(), 2);
        assertThat(page2.items()).extracting(NewsCommentItem::commentId).containsExactly(first);
        assertThat(page2.items().get(0).mine()).isTrue();
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("종료된 리그에서도 댓글을 조회할 수 있다")
    void listInEndedLeague() {
        long news = publishedNews(STOCK_ID);
        comment(news, ME_PLAYER, "댓글");
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        assertThat(service.findByNews(OTHER, LEAGUE_ID, STOCK_ID, news, null, 20).items())
                .hasSize(1);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 댓글을 쓰거나 볼 수 없다")
    void outsider() {
        long news = publishedNews(STOCK_ID);
        given(leagueQueryService.getPlayers(OUTSIDER, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));

        assertBiz(() -> service.write(OUTSIDER, LEAGUE_ID, STOCK_ID, news, "댓글"),
                ErrorCode.FORBIDDEN);
        assertBiz(() -> service.findByNews(OUTSIDER, LEAGUE_ID, STOCK_ID, news, null, 20),
                ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("다른 종목 경로의 뉴스 댓글은 조회할 수 없다")
    void otherStock() {
        long news = publishedNews(STOCK_ID);

        assertBiz(() -> service.findByNews(ME, LEAGUE_ID, OTHER_STOCK_ID, news, null, 20),
                ErrorCode.NEWS_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제한 댓글은 목록에서 빠지고, 종료된 리그에서도 삭제할 수 있다")
    void delete() {
        long news = publishedNews(STOCK_ID);
        final long kept = comment(news, OTHER_PLAYER, "남김");
        final long deleted = comment(news, ME_PLAYER, "삭제");
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        service.delete(ME, LEAGUE_ID, STOCK_ID, news, deleted);
        entityManager.flush();

        assertThat(entityManager.find(NewsComment.class, deleted).getStatus())
                .isEqualTo(CommentStatus.DELETED);
        assertThat(service.findByNews(ME, LEAGUE_ID, STOCK_ID, news, null, 20).items())
                .extracting(NewsCommentItem::commentId).containsExactly(kept);
        assertBiz(() -> service.delete(ME, LEAGUE_ID, STOCK_ID, news, deleted),
                ErrorCode.COMMENT_NOT_FOUND);
        assertBiz(() -> service.delete(ME, LEAGUE_ID, STOCK_ID, news, kept),
                ErrorCode.COMMENT_NOT_AUTHOR);
    }

    @Test
    @DisplayName("다른 뉴스의 댓글 ID로는 삭제할 수 없다")
    void deleteOtherNewsComment() {
        long news = publishedNews(STOCK_ID);
        long otherNews = publishedNews(STOCK_ID);
        long comment = comment(otherNews, ME_PLAYER, "댓글");

        assertBiz(() -> service.delete(ME, LEAGUE_ID, STOCK_ID, news, comment),
                ErrorCode.COMMENT_NOT_FOUND);
    }

    private static LeaguePlayers players() {
        return new LeaguePlayers(LEAGUE_ID, false, null, List.of(
                new LeaguePlayer(ME_PLAYER, "나", false, true, NOW, NOW),
                new LeaguePlayer(OTHER_PLAYER, "상대", false, true, NOW, NOW),
                new LeaguePlayer(99L, "탈퇴한 사용자", false, true, NOW, NOW)));
    }

    private long publishedNews(long stockId) {
        return news(stockId, new NewsGenerationResult.Generated("제목", "본문",
                NewsSentiment.POSITIVE, "요약"));
    }

    private long news(long stockId, NewsGenerationResult result) {
        Disclosure disclosure = entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID,
                stockId, ME_PLAYER, DisclosureTitle.of("공시"), DisclosureBody.of("본문"), 2, NOW));
        News news = entityManager.persistAndFlush(News.requestFrom(disclosure, NOW));
        news.complete(result, NOW);
        entityManager.flush();
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
