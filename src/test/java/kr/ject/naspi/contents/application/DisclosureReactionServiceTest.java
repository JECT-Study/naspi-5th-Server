package kr.ject.naspi.contents.application;

import static kr.ject.naspi.contents.domain.DisclosureReactionType.DISLIKE;
import static kr.ject.naspi.contents.domain.DisclosureReactionType.LIKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureReactionType;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import(DisclosureReactionService.class)
class DisclosureReactionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final long LEAGUE_ID = 3L;
    private static final long STOCK_ID = 11L;
    private static final long AUTHOR_MEMBER_ID = 7L;
    private static final long AUTHOR_PLAYER_ID = 70L;
    private static final long MEMBER_A = 8L;
    private static final long PLAYER_A = 80L;
    private static final long MEMBER_B = 9L;
    private static final long PLAYER_B = 90L;
    private static final long OUTSIDER_MEMBER_ID = 10L;

    @Autowired
    private DisclosureReactionService reactionService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    private long disclosureId;

    @BeforeEach
    void setUp() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.of(
                new LeagueTradingDay(2, 7, NOW.plusHours(23), NOW.minusDays(1),
                        NOW.plusDays(6))));
        given(leagueQueryService.requireAccessiblePlayer(AUTHOR_MEMBER_ID, LEAGUE_ID))
                .willReturn(AUTHOR_PLAYER_ID);
        given(leagueQueryService.requireAccessiblePlayer(MEMBER_A, LEAGUE_ID))
                .willReturn(PLAYER_A);
        given(leagueQueryService.requireAccessiblePlayer(MEMBER_B, LEAGUE_ID))
                .willReturn(PLAYER_B);
        given(leagueQueryService.requireAccessiblePlayer(OUTSIDER_MEMBER_ID, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));
        disclosureId = entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID, STOCK_ID,
                AUTHOR_PLAYER_ID, DisclosureTitle.of("제목"), DisclosureBody.of("본문"), 2, NOW))
                .getId();
    }

    @Test
    @DisplayName("참여자는 공시에 호재 또는 악재로 반응하고, 반응 수가 집계된다")
    void react() {
        DisclosureReactionSummary good = reactAs(MEMBER_A, LIKE);
        assertThat(good).isEqualTo(new DisclosureReactionSummary(disclosureId, 1, 0, LIKE));

        DisclosureReactionSummary bad = reactAs(MEMBER_B, DISLIKE);
        assertThat(bad).isEqualTo(new DisclosureReactionSummary(disclosureId, 1, 1, DISLIKE));
    }

    @Test
    @DisplayName("공시 1건에 한 사람은 호재·악재 중 하나만 등록할 수 있다 (다른 반응을 누르면 바뀐다)")
    void onePerPlayer() {
        reactAs(MEMBER_A, LIKE);

        DisclosureReactionSummary changed = reactAs(MEMBER_A, DISLIKE);

        assertThat(changed).isEqualTo(new DisclosureReactionSummary(disclosureId, 0, 1, DISLIKE));
        assertThat(reactionRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 반응 버튼을 다시 누르면 반응이 취소되고, 한 번 더 누르면 다시 등록된다 (토글)")
    void sameButtonToggles() {
        reactAs(MEMBER_A, LIKE);
        reactAs(MEMBER_B, LIKE);

        DisclosureReactionSummary cancelled = reactAs(MEMBER_A, LIKE);
        assertThat(cancelled).isEqualTo(new DisclosureReactionSummary(disclosureId, 1, 0, null));
        assertThat(reactionRows()).isEqualTo(1);

        DisclosureReactionSummary again = reactAs(MEMBER_A, LIKE);
        assertThat(again).isEqualTo(new DisclosureReactionSummary(disclosureId, 2, 0, LIKE));
    }

    @Test
    @DisplayName("공시 작성자도 본인 공시에 반응할 수 있다")
    void reactToOwnDisclosure() {
        DisclosureReactionSummary summary = reactAs(AUTHOR_MEMBER_ID, LIKE);

        assertThat(summary).isEqualTo(new DisclosureReactionSummary(disclosureId, 1, 0, LIKE));
    }

    @Test
    @DisplayName("반응은 AI 뉴스 생성에 영향을 주지 않는다 (반응·취소해도 이벤트를 발행하지 않는다)")
    void reactionDoesNotAffectNews() {
        reactAs(MEMBER_A, LIKE);
        reactAs(MEMBER_A, DISLIKE);
        reactAs(MEMBER_A, DISLIKE);

        // 테스트 프레임워크의 생명주기 이벤트는 빼고, 애플리케이션이 발행한 이벤트만 본다
        assertThat(events.stream(DisclosurePublished.class)).isEmpty();
        assertThat(events.stream()
                .filter(event -> event.getClass().getName().startsWith("kr.ject.naspi")))
                .isEmpty();
    }

    @Test
    @DisplayName("진행 중인 리그에서만 반응을 등록·변경·취소할 수 있다 (대기실·종료된 리그는 불가)")
    void leagueNotInProgress() {
        reactAs(MEMBER_A, LIKE);
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        // 변경(DISLIKE)도 취소(LIKE 다시 누름)도 거절하고 기존 반응을 그대로 둔다
        assertBiz(() -> reactAs(MEMBER_A, DISLIKE),
                ErrorCode.DISCLOSURE_REACTION_LEAGUE_NOT_STARTED);
        assertBiz(() -> reactAs(MEMBER_A, LIKE), ErrorCode.DISCLOSURE_REACTION_LEAGUE_NOT_STARTED);
        assertThat(reactionRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 공시에 반응할 수 없다")
    void outsider() {
        assertBiz(() -> reactAs(OUTSIDER_MEMBER_ID, LIKE), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("삭제된 공시, 없는 공시, 다른 종목의 공시에는 반응할 수 없다")
    void disclosureNotFound() {
        assertBiz(() -> reactionService.toggle(MEMBER_A, LEAGUE_ID, STOCK_ID + 1, disclosureId,
                LIKE), ErrorCode.DISCLOSURE_NOT_FOUND);
        assertBiz(() -> reactionService.toggle(MEMBER_A, LEAGUE_ID, STOCK_ID, 999_999L, LIKE),
                ErrorCode.DISCLOSURE_NOT_FOUND);

        entityManager.find(Disclosure.class, disclosureId).delete(AUTHOR_PLAYER_ID);
        entityManager.flush();

        assertBiz(() -> reactAs(MEMBER_A, LIKE), ErrorCode.DISCLOSURE_NOT_FOUND);
        assertThat(reactionRows()).isZero();
    }

    private DisclosureReactionSummary reactAs(long memberId, DisclosureReactionType type) {
        return reactionService.toggle(memberId, LEAGUE_ID, STOCK_ID, disclosureId, type);
    }

    private long reactionRows() {
        return entityManager.getEntityManager()
                .createQuery("select count(r) from DisclosureReaction r", Long.class)
                .getSingleResult();
    }

    private static void assertBiz(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
