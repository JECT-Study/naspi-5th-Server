package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
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
import kr.ject.naspi.contents.domain.NewsGenerationResult;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
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
@Import({DisclosureQueryService.class, ImageService.class, DisclosureReactionService.class,
        DisclosureCommentService.class, DisclosureQueryServiceTest.Config.class})
class DisclosureQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final long LEAGUE_ID = 3L;
    private static final long STOCK_ID = 11L;
    private static final long OTHER_STOCK_ID = 12L;
    private static final long AUTHOR_MEMBER_ID = 7L;
    private static final long AUTHOR_PLAYER_ID = 70L;
    private static final long VIEWER_MEMBER_ID = 8L;
    private static final long VIEWER_PLAYER_ID = 80L;
    private static final long OUTSIDER_MEMBER_ID = 9L;

    @Autowired
    private DisclosureQueryService queryService;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private ImageStorage imageStorage;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @BeforeEach
    void setUp() {
        given(leagueQueryService.findPlayerId(AUTHOR_MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.of(AUTHOR_PLAYER_ID));
        given(leagueQueryService.findPlayerId(VIEWER_MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.of(VIEWER_PLAYER_ID));
        given(leagueQueryService.findPlayerId(OUTSIDER_MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.empty());
        given(imageStorage.signedUrl(anyString(), any()))
                .willAnswer(invocation -> "https://signed/" + invocation.getArgument(0));
    }

    @Test
    @DisplayName("참여자는 종목에 등록된 공시를 최신순으로 조회하고, 제목·본문·작성 정보·반응 수를 확인할 수 있다")
    void findByStock() {
        final long older = publish(STOCK_ID, "처음 공시", 1);
        long newer = publish(STOCK_ID, "두 번째 공시", 2);
        publish(OTHER_STOCK_ID, "다른 종목", 2);
        react(newer, AUTHOR_PLAYER_ID, DisclosureReactionType.LIKE);
        react(newer, VIEWER_PLAYER_ID, DisclosureReactionType.DISLIKE);

        DisclosurePage page = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                null, 20);

        assertThat(page.items()).extracting(DisclosureListItem::disclosureId)
                .containsExactly(newer, older);
        DisclosureListItem first = page.items().get(0);
        assertThat(first.title()).isEqualTo("두 번째 공시");
        assertThat(first.body()).isEqualTo("본문");
        assertThat(first.tradingDay()).isEqualTo(2);
        assertThat(first.createdAt()).isEqualTo(NOW);
        assertThat(first.authorPlayerId()).isEqualTo(AUTHOR_PLAYER_ID);
        assertThat(first.mine()).isFalse();
        assertThat(first.reaction()).isEqualTo(new DisclosureReactionSummary(newer, 1, 1,
                DisclosureReactionType.DISLIKE));
        assertThat(page.items().get(1).reaction())
                .isEqualTo(new DisclosureReactionSummary(older, 0, 0, null));
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("공시 목록은 공시마다 댓글 수와 가장 최근 댓글을, 상세는 댓글 전체를 주고, 연결된 뉴스는 상세에만 준다")
    void comments() {
        long withComments = publish(STOCK_ID, "댓글 있음", 2);
        long withoutComments = publish(STOCK_ID, "댓글 없음", 2);
        requestNews(withComments);
        long first = comment(withComments, AUTHOR_PLAYER_ID, "처음");
        long latest = comment(withComments, VIEWER_PLAYER_ID, "최근");

        DisclosurePage page = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                null, 20);

        assertThat(page.items()).extracting(DisclosureListItem::disclosureId)
                .containsExactly(withoutComments, withComments);
        assertThat(page.items().get(0).comments()).isEqualTo(new CommentPreview(0, null));
        CommentPreview preview = page.items().get(1).comments();
        assertThat(preview.commentCount()).isEqualTo(2);
        assertThat(preview.latestComment().commentId()).isEqualTo(latest);
        assertThat(preview.latestComment().mine()).isTrue();
        DisclosureDetail detail = queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                withComments);
        assertThat(detail.comments()).extracting(CommentItem::commentId)
                .containsExactly(latest, first);
        assertThat(detail.news()).isNotNull();
    }

    @Test
    @DisplayName("공시 목록은 커서로 이어서 조회하고, 마지막 페이지면 다음 커서가 없다")
    void cursorPaging() {
        long first = publish(STOCK_ID, "1", 1);
        long second = publish(STOCK_ID, "2", 1);
        long third = publish(STOCK_ID, "3", 1);

        DisclosurePage page1 = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                null, 2);
        assertThat(page1.items()).extracting(DisclosureListItem::disclosureId)
                .containsExactly(third, second);
        assertThat(page1.nextCursor()).isEqualTo(second);

        DisclosurePage page2 = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                page1.nextCursor(), 2);
        assertThat(page2.items()).extracting(DisclosureListItem::disclosureId)
                .containsExactly(first);
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("공시 목록에는 첨부 이미지 전체를 첨부 순서대로 담고, 이미지가 없으면 빈 목록이다")
    void listImages() {
        long withImages = publish(STOCK_ID, "이미지 공시", 1);
        attachImages(withImages, 3);
        long withoutImages = publish(STOCK_ID, "글만", 1);

        DisclosurePage page = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                null, 20);

        DisclosureListItem noImage = page.items().get(0);
        assertThat(noImage.disclosureId()).isEqualTo(withoutImages);
        assertThat(noImage.images()).isEmpty();
        DisclosureListItem image = page.items().get(1);
        assertThat(image.images()).extracting(ImageUrl::signedUrl).containsExactly(
                "https://signed/key-" + withImages + "-0",
                "https://signed/key-" + withImages + "-1",
                "https://signed/key-" + withImages + "-2");
        then(imageStorage).should(times(3)).signedUrl(anyString(), any());
    }

    @Test
    @DisplayName("삭제된 공시는 목록에 나오지 않고 상세 내용도 조회할 수 없다")
    void deletedDisclosureHidden() {
        long kept = publish(STOCK_ID, "남은 공시", 1);
        long deleted = publish(STOCK_ID, "지운 공시", 1);
        entityManager.find(Disclosure.class, deleted).delete(AUTHOR_PLAYER_ID);
        entityManager.flush();

        DisclosurePage page = queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                null, 20);
        assertThat(page.items()).extracting(DisclosureListItem::disclosureId)
                .containsExactly(kept);

        assertBiz(() -> queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, deleted),
                ErrorCode.DISCLOSURE_NOT_FOUND);
    }

    @Test
    @DisplayName("공시 상세에서 제목·본문·전체 첨부 이미지(첨부 순서)·작성 정보·반응을 확인할 수 있다")
    void getDetail() {
        long id = publish(STOCK_ID, "상세 공시", 3);
        attachImages(id, 2);
        react(id, VIEWER_PLAYER_ID, DisclosureReactionType.LIKE);

        DisclosureDetail detail = queryService.getDetail(AUTHOR_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                id);

        assertThat(detail.title()).isEqualTo("상세 공시");
        assertThat(detail.body()).isEqualTo("본문");
        assertThat(detail.tradingDay()).isEqualTo(3);
        assertThat(detail.createdAt()).isEqualTo(NOW);
        assertThat(detail.authorPlayerId()).isEqualTo(AUTHOR_PLAYER_ID);
        assertThat(detail.mine()).isTrue();
        assertThat(detail.images()).extracting(ImageUrl::signedUrl).containsExactly(
                "https://signed/key-" + id + "-0", "https://signed/key-" + id + "-1");
        assertThat(detail.reaction()).isEqualTo(new DisclosureReactionSummary(id, 1, 0, null));
    }

    @Test
    @DisplayName("공시 상세에서 공시로 만든 AI 뉴스의 제목·본문·호재/악재를 확인할 수 있다")
    void getDetailWithPublishedNews() {
        long id = publish(STOCK_ID, "상세 공시", 1);
        News news = requestNews(id);
        news.complete(new NewsGenerationResult.Generated("뉴스 제목", "뉴스 본문",
                NewsSentiment.POSITIVE, "요약"), NOW);
        entityManager.flush();

        DisclosureDetail detail = queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID,
                id);

        assertThat(detail.news()).isEqualTo(new DisclosureNews(news.getId(),
                NewsStatus.PUBLISHED, "뉴스 제목", "뉴스 본문", NewsSentiment.POSITIVE));
    }

    @Test
    @DisplayName("AI 뉴스가 발행되기 전에는 생성 상태만 확인할 수 있고 제목·본문·호재/악재는 없다")
    void getDetailWithUnpublishedNews() {
        long pending = publish(STOCK_ID, "생성 중", 1);
        News pendingNews = requestNews(pending);
        long failed = publish(STOCK_ID, "실패", 1);
        News failedNews = requestNews(failed);
        failedNews.complete(new NewsGenerationResult.Failed("오류"), NOW);
        entityManager.flush();

        assertThat(queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, pending).news())
                .isEqualTo(new DisclosureNews(pendingNews.getId(), NewsStatus.PENDING, null, null,
                        null));
        assertThat(queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, failed).news())
                .isEqualTo(new DisclosureNews(failedNews.getId(), NewsStatus.FAILED, null, null,
                        null));
    }

    @Test
    @DisplayName("뉴스가 없는 공시의 상세는 뉴스 없이 조회된다")
    void getDetailWithoutNews() {
        long id = publish(STOCK_ID, "뉴스 없음", 1);

        assertThat(queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id).news())
                .isNull();
    }

    @Test
    @DisplayName("다른 종목의 공시 ID나 없는 공시는 상세 조회할 수 없다")
    void detailNotFound() {
        long id = publish(STOCK_ID, "공시", 1);

        assertBiz(() -> queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, OTHER_STOCK_ID, id),
                ErrorCode.DISCLOSURE_NOT_FOUND);
        assertBiz(() -> queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, 999_999L),
                ErrorCode.DISCLOSURE_NOT_FOUND);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 공시 목록과 상세를 조회할 수 없다")
    void outsider() {
        long id = publish(STOCK_ID, "공시", 1);

        assertBiz(() -> queryService.findByStock(OUTSIDER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null,
                20), ErrorCode.FORBIDDEN);
        assertBiz(() -> queryService.getDetail(OUTSIDER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id),
                ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("종료된 리그의 공시도 조회할 수 있다 (리그 진행 상태와 무관)")
    void endedLeague() {
        long id = publish(STOCK_ID, "공시", 7);
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        assertThat(queryService.findByStock(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, null, 20)
                .items()).hasSize(1);
        assertThat(queryService.getDetail(VIEWER_MEMBER_ID, LEAGUE_ID, STOCK_ID, id).title())
                .isEqualTo("공시");
    }

    private long publish(long stockId, String title, int tradingDay) {
        return entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID, stockId,
                AUTHOR_PLAYER_ID, DisclosureTitle.of(title), DisclosureBody.of("본문"),
                tradingDay, NOW)).getId();
    }

    private News requestNews(long disclosureId) {
        return entityManager.persistAndFlush(News.requestFrom(
                entityManager.find(Disclosure.class, disclosureId), NOW));
    }

    private long comment(long disclosureId, long playerId, String content) {
        Disclosure disclosure = entityManager.find(Disclosure.class, disclosureId);
        return entityManager.persistAndFlush(DisclosureComment.write(disclosure, playerId,
                CommentContent.of(content), NOW)).getId();
    }

    private void attachImages(long disclosureId, int count) {
        for (int i = 0; i < count; i++) {
            entityManager.persist(Image.attach(LEAGUE_ID, disclosureId, i,
                    "key-" + disclosureId + "-" + i, "a.png", ImageFormat.PNG, 10, NOW));
        }
        entityManager.flush();
    }

    private void react(long disclosureId, long playerId, DisclosureReactionType type) {
        entityManager.persistAndFlush(DisclosureReaction.of(LEAGUE_ID, disclosureId, playerId,
                type));
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
