package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ject.naspi.contents.domain.CommentContent;
import kr.ject.naspi.contents.domain.CommentStatus;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureComment;
import kr.ject.naspi.contents.domain.DisclosureTitle;
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

@ActiveProfiles("test")
@DataJpaTest
@Import({DisclosureCommentService.class, DisclosureCommentServiceTest.Config.class})
class DisclosureCommentServiceTest {

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
    private DisclosureCommentService service;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @BeforeEach
    void setUp() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID))
                .willReturn(Optional.of(new LeagueTradingDay(2, 7, NOW.plusDays(1),
                        NOW.minusDays(1), NOW.plusDays(6))));
        given(leagueQueryService.requireAccessiblePlayer(ME, LEAGUE_ID)).willReturn(ME_PLAYER);
        given(leagueQueryService.requireAccessiblePlayer(OTHER, LEAGUE_ID))
                .willReturn(OTHER_PLAYER);
        given(leagueQueryService.requireAccessiblePlayer(OUTSIDER, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));
        given(leagueQueryService.getPlayerNames(eq(LEAGUE_ID), anyCollection()))
                .willReturn(Map.of(ME_PLAYER, "나", OTHER_PLAYER, "상대"));
    }

    @Test
    @DisplayName("리그 참여자는 종목과 관계없이 공시에 댓글을 쓸 수 있다")
    void write() {
        long disclosure = disclosure(STOCK_ID);

        CommentItem item = service.write(OTHER, LEAGUE_ID, STOCK_ID, disclosure, "첫 댓글");

        assertThat(item.content()).isEqualTo("첫 댓글");
        assertThat(item.authorPlayerId()).isEqualTo(OTHER_PLAYER);
        assertThat(item.authorName()).isEqualTo("상대");
        assertThat(item.mine()).isTrue();
        assertThat(item.createdAt()).isEqualTo(NOW);
        assertThat(entityManager.find(DisclosureComment.class, item.commentId())
                .getDisclosureId()).isEqualTo(disclosure);
    }

    @Test
    @DisplayName("시작 전(대기실) 리그에서는 공시에 댓글을 쓸 수 없다")
    void leagueNotStarted() {
        long disclosure = disclosure(STOCK_ID);
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        assertBiz(() -> service.write(ME, LEAGUE_ID, STOCK_ID, disclosure, "댓글"),
                ErrorCode.COMMENT_LEAGUE_NOT_STARTED);
    }

    @Test
    @DisplayName("종료 시각이 지난 리그에서는 공시에 댓글을 쓸 수 없고 리그 종료로 거절된다")
    void writeInEndedLeague() {
        long disclosure = disclosure(STOCK_ID);
        // 종료 시각이 지난 리그는 거래일이 없고, 결과 페이지 외 리그 화면에 접근할 수 없다
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());
        given(leagueQueryService.requireAccessiblePlayer(ME, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_ENDED));

        assertBiz(() -> service.write(ME, LEAGUE_ID, STOCK_ID, disclosure, "댓글"),
                ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("공시 댓글 목록은 페이징 없이 전체를 오래된 순으로 주고 작성자 이름과 내 댓글 여부를 표시한다")
    void list() {
        long disclosure = disclosure(STOCK_ID);
        long first = comment(disclosure, ME_PLAYER, "1");
        long second = comment(disclosure, OTHER_PLAYER, "2");

        List<CommentItem> comments = service.findByTarget(ME, LEAGUE_ID, STOCK_ID, disclosure);

        assertThat(comments).extracting(CommentItem::commentId).containsExactly(first, second);
        assertThat(comments).extracting(CommentItem::authorName).containsExactly("나", "상대");
        assertThat(comments).extracting(CommentItem::mine).containsExactly(true, false);
    }

    @Test
    @DisplayName("종료된 리그의 기록(댓글)은 조회하거나 삭제할 수 없다")
    void endedLeagueBlocked() {
        long disclosure = disclosure(STOCK_ID);
        final long comment = comment(disclosure, ME_PLAYER, "댓글");
        given(leagueQueryService.requireAccessiblePlayer(ME, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_ENDED));

        assertBiz(() -> service.findByTarget(ME, LEAGUE_ID, STOCK_ID, disclosure),
                ErrorCode.LEAGUE_ALREADY_ENDED);
        assertBiz(() -> service.delete(ME, LEAGUE_ID, STOCK_ID, disclosure, comment),
                ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("공시가 삭제되면 달린 댓글을 쓰거나 보거나 삭제할 수 없다")
    void deletedDisclosure() {
        long disclosure = disclosure(STOCK_ID);
        final long comment = comment(disclosure, ME_PLAYER, "댓글");
        entityManager.find(Disclosure.class, disclosure).delete(ME_PLAYER);
        entityManager.flush();

        assertBiz(() -> service.write(ME, LEAGUE_ID, STOCK_ID, disclosure, "댓글"),
                ErrorCode.DISCLOSURE_NOT_FOUND);
        assertBiz(() -> service.findByTarget(ME, LEAGUE_ID, STOCK_ID, disclosure),
                ErrorCode.DISCLOSURE_NOT_FOUND);
        assertBiz(() -> service.delete(ME, LEAGUE_ID, STOCK_ID, disclosure, comment),
                ErrorCode.DISCLOSURE_NOT_FOUND);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 공시 댓글을 쓰거나 볼 수 없다")
    void outsider() {
        long disclosure = disclosure(STOCK_ID);

        assertBiz(() -> service.write(OUTSIDER, LEAGUE_ID, STOCK_ID, disclosure, "댓글"),
                ErrorCode.FORBIDDEN);
        assertBiz(() -> service.findByTarget(OUTSIDER, LEAGUE_ID, STOCK_ID, disclosure),
                ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("다른 종목 경로의 공시 댓글은 조회할 수 없다")
    void otherStock() {
        long disclosure = disclosure(STOCK_ID);

        assertBiz(() -> service.findByTarget(ME, LEAGUE_ID, OTHER_STOCK_ID, disclosure),
                ErrorCode.DISCLOSURE_NOT_FOUND);
    }

    @Test
    @DisplayName("공시 댓글은 작성자 본인만 삭제할 수 있고, 삭제한 댓글은 목록에서 빠지며, 다른 공시의 댓글 ID로는 삭제할 수 없다")
    void delete() {
        long disclosure = disclosure(STOCK_ID);
        long otherDisclosure = disclosure(STOCK_ID);
        final long kept = comment(disclosure, OTHER_PLAYER, "남김");
        final long deleted = comment(disclosure, ME_PLAYER, "삭제");
        final long elsewhere = comment(otherDisclosure, ME_PLAYER, "다른 공시");

        service.delete(ME, LEAGUE_ID, STOCK_ID, disclosure, deleted);
        entityManager.flush();

        assertThat(entityManager.find(DisclosureComment.class, deleted).getStatus())
                .isEqualTo(CommentStatus.DELETED);
        assertThat(service.findByTarget(ME, LEAGUE_ID, STOCK_ID, disclosure))
                .extracting(CommentItem::commentId).containsExactly(kept);
        assertBiz(() -> service.delete(ME, LEAGUE_ID, STOCK_ID, disclosure, kept),
                ErrorCode.COMMENT_NOT_AUTHOR);
        assertBiz(() -> service.delete(ME, LEAGUE_ID, STOCK_ID, disclosure, elsewhere),
                ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("목록용 미리보기는 공시별 댓글 수와 가장 먼저 등록된 댓글 1개를 주고, 댓글이 없으면 0개다")
    void previews() {
        long disclosure = disclosure(STOCK_ID);
        long empty = disclosure(STOCK_ID);
        long first = comment(disclosure, ME_PLAYER, "처음");
        comment(disclosure, OTHER_PLAYER, "나중");

        Map<Long, CommentPreview> previews = service.previews(LEAGUE_ID,
                List.of(disclosure, empty), ME_PLAYER);

        assertThat(previews.get(disclosure).commentCount()).isEqualTo(2);
        assertThat(previews.get(disclosure).previewComment().commentId()).isEqualTo(first);
        assertThat(previews.get(disclosure).previewComment().mine()).isTrue();
        assertThat(previews.get(empty)).isEqualTo(new CommentPreview(0, null));
    }

    private long disclosure(long stockId) {
        return entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID, stockId, ME_PLAYER,
                DisclosureTitle.of("공시"), DisclosureBody.of("본문"), 2, NOW)).getId();
    }

    private long comment(long disclosureId, long playerId, String content) {
        Disclosure disclosure = entityManager.find(Disclosure.class, disclosureId);
        return entityManager.persistAndFlush(DisclosureComment.write(disclosure, playerId,
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
