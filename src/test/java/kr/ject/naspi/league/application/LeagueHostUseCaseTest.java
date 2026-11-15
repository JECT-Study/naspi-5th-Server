package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.StockListingService;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueHostUseCase.class, LeagueLeaveUseCase.class, LeagueStartUseCase.class,
        LeagueService.class, LeagueCreationUseCase.class, InviteCodeIssuer.class,
        LeagueJoinLimit.class, StockListingService.class, StockQueryService.class,
        LocalStockSnapshotCache.class, MutableClock.Config.class})
class LeagueHostUseCaseTest {

    private static final long HOST_MEMBER_ID = 1L;

    @Autowired
    private LeagueHostUseCase useCaseUnderTest;

    @Autowired
    private LeagueLeaveUseCase leagueLeaveUseCase;

    @Autowired
    private LeagueStartUseCase leagueStartUseCase;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueCreationUseCase leagueCreationUseCase;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MutableClock clock;

    private long nextMemberId = 100L;

    // 리그 이름 수정

    @Test
    @DisplayName("방장은 대기실에서 리그 이름을 수정할 수 있다")
    void hostRenames() {
        CreatedLeague created = createLeague();

        RenamedLeague renamed = useCaseUnderTest.rename(HOST_MEMBER_ID, created.leagueId(), "새 이름");

        assertThat(renamed).isEqualTo(new RenamedLeague(created.leagueId(), "새 이름"));
        flushAndClear();
        assertThat(entityManager.find(League.class, created.leagueId()).getName().getValue())
                .isEqualTo("새 이름");
    }

    @Test
    @DisplayName("리그 이름 수정에도 최대 12자 규칙이 적용된다")
    void renameValidatesName() {
        CreatedLeague created = createLeague();

        String tooLong = "가".repeat(13);

        assertFails(() -> useCaseUnderTest.rename(HOST_MEMBER_ID, created.leagueId(), tooLong),
                ErrorCode.INVALID_LEAGUE_NAME);
    }

    @Test
    @DisplayName("방장이 아닌 참여자와 대기자는 리그 이름을 수정할 수 없다")
    void nonHostCannotRename() {
        CreatedLeague created = createLeague();
        long participantMemberId = nextMemberId;
        joinListed(created);
        long waitingMemberId = nextMemberId;
        leagueService.join(nextMemberId++, created.inviteCode());

        assertFails(() -> useCaseUnderTest.rename(participantMemberId, created.leagueId(), "이름"),
                ErrorCode.LEAGUE_HOST_ONLY);
        assertFails(() -> useCaseUnderTest.rename(waitingMemberId, created.leagueId(), "이름"),
                ErrorCode.LEAGUE_HOST_ONLY);
    }

    @Test
    @DisplayName("리그 시작 이후에는 방장도 리그 이름을 수정할 수 없다")
    void cannotRenameAfterStart() {
        CreatedLeague created = startedLeague();

        assertFails(() -> useCaseUnderTest.rename(HOST_MEMBER_ID, created.leagueId(), "이름"),
                ErrorCode.LEAGUE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("방장이 위임되면 새 방장이 리그 이름을 수정할 수 있다")
    void delegatedHostRenames() {
        CreatedLeague created = createLeague();
        long nextHostMemberId = nextMemberId;
        joinListed(created);
        leagueLeaveUseCase.leave(HOST_MEMBER_ID, created.leagueId());

        RenamedLeague renamed = useCaseUnderTest.rename(nextHostMemberId, created.leagueId(), "위임");

        assertThat(renamed.name()).isEqualTo("위임");
    }

    // 리그 삭제

    @Test
    @DisplayName("방장은 대기실에서 리그를 삭제할 수 있고, 삭제되면 초대 코드가 폐기되어 아무도 접근할 수 없다")
    void hostDeletes() {
        CreatedLeague created = createLeague();
        final long participantMemberId = nextMemberId;
        joinListed(created);

        useCaseUnderTest.delete(HOST_MEMBER_ID, created.leagueId());

        flushAndClear();
        League league = entityManager.find(League.class, created.leagueId());
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.DELETED);
        assertThat(league.getInviteCode()).isNull();
        assertThat(league.getHostPlayerId()).isNull();
        assertFails(() -> leagueLeaveUseCase.leave(participantMemberId, created.leagueId()),
                ErrorCode.LEAGUE_NOT_FOUND);
        assertFails(() -> leagueService.join(nextMemberId++, created.inviteCode()),
                ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("방장이 아닌 참여자는 리그를 삭제할 수 없다")
    void nonHostCannotDelete() {
        CreatedLeague created = createLeague();
        long participantMemberId = nextMemberId;
        joinListed(created);

        assertFails(() -> useCaseUnderTest.delete(participantMemberId, created.leagueId()),
                ErrorCode.LEAGUE_HOST_ONLY);
    }

    @Test
    @DisplayName("리그 시작 이후에는 방장도 리그를 삭제할 수 없다")
    void cannotDeleteAfterStart() {
        CreatedLeague created = startedLeague();

        assertFails(() -> useCaseUnderTest.delete(HOST_MEMBER_ID, created.leagueId()),
                ErrorCode.LEAGUE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("이미 삭제된 대기실은 다시 삭제할 수 없다")
    void cannotDeleteTwice() {
        CreatedLeague created = createLeague();
        useCaseUnderTest.delete(HOST_MEMBER_ID, created.leagueId());

        assertFails(() -> useCaseUnderTest.delete(HOST_MEMBER_ID, created.leagueId()),
                ErrorCode.LEAGUE_NOT_FOUND);
    }

    // 플레이어 추방

    @Test
    @DisplayName("방장은 대기실에서 상장을 마친 참여자를 추방할 수 있다")
    void hostKicksParticipant() {
        CreatedLeague created = createLeague();
        JoinedLeague participant = joinListed(created);

        useCaseUnderTest.kick(HOST_MEMBER_ID, created.leagueId(), participant.playerId());

        flushAndClear();
        assertThat(entityManager.find(Player.class, participant.playerId())).isNull();
    }

    @Test
    @DisplayName("방장은 대기실에서 상장 전인 대기자도 추방할 수 있다")
    void hostKicksWaitingPlayer() {
        CreatedLeague created = createLeague();
        JoinedLeague waiting = leagueService.join(nextMemberId++, created.inviteCode());

        useCaseUnderTest.kick(HOST_MEMBER_ID, created.leagueId(), waiting.playerId());

        flushAndClear();
        assertThat(entityManager.find(Player.class, waiting.playerId())).isNull();
    }

    // TODO: market에 종목 삭제 서비스가 생기면 "추방된 참여자의 종목도 삭제된다" 테스트를 추가한다.

    @Test
    @DisplayName("방장은 자기 자신을 추방할 수 없다")
    void hostCannotKickSelf() {
        CreatedLeague created = createLeague();

        assertFails(() -> useCaseUnderTest.kick(
                HOST_MEMBER_ID, created.leagueId(), created.hostPlayerId()),
                ErrorCode.LEAGUE_HOST_NOT_KICKABLE);
    }

    @Test
    @DisplayName("방장이 아닌 참여자는 다른 플레이어를 추방할 수 없다")
    void nonHostCannotKick() {
        CreatedLeague created = createLeague();
        long participantMemberId = nextMemberId;
        joinListed(created);
        JoinedLeague waiting = leagueService.join(nextMemberId++, created.inviteCode());

        assertFails(() -> useCaseUnderTest.kick(
                participantMemberId, created.leagueId(), waiting.playerId()),
                ErrorCode.LEAGUE_HOST_ONLY);
    }

    @Test
    @DisplayName("리그 시작 이후에는 방장도 참여자를 추방할 수 없다")
    void cannotKickAfterStart() {
        CreatedLeague created = createLeague();
        JoinedLeague participant = joinListed(created);
        joinListed(created);
        leagueStartUseCase.start(HOST_MEMBER_ID, created.leagueId());

        assertFails(() -> useCaseUnderTest.kick(
                HOST_MEMBER_ID, created.leagueId(), participant.playerId()),
                ErrorCode.LEAGUE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("다른 리그의 플레이어는 추방할 수 없다")
    void cannotKickPlayerOfOtherLeague() {
        CreatedLeague created = createLeague();
        CreatedLeague other = leagueCreationUseCase.create(nextMemberId++, "다른 리그", "다른종목", "소개");
        JoinedLeague otherPlayer = leagueService.join(nextMemberId++, other.inviteCode());

        assertFails(() -> useCaseUnderTest.kick(
                HOST_MEMBER_ID, created.leagueId(), otherPlayer.playerId()),
                ErrorCode.PLAYER_NOT_FOUND);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 방장 기능을 쓸 수 없다")
    void outsiderForbidden() {
        CreatedLeague created = createLeague();

        assertFails(() -> useCaseUnderTest.delete(nextMemberId++, created.leagueId()),
                ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("생성 후 7일이 지나 만료된 대기실에서는 방장 기능을 쓸 수 없다")
    void expiredWaitingRoom() {
        CreatedLeague created = createLeague();
        clock.advance(League.WAITING_ROOM_LIFETIME);

        assertFails(() -> useCaseUnderTest.rename(HOST_MEMBER_ID, created.leagueId(), "이름"),
                ErrorCode.LEAGUE_NOT_FOUND);
    }

    private void assertFails(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    private CreatedLeague createLeague() {
        return leagueCreationUseCase.create(HOST_MEMBER_ID, "리그", "나스피", "소개");
    }

    /** 참여자 3명으로 시작한 리그를 만든다. */
    private CreatedLeague startedLeague() {
        CreatedLeague created = createLeague();
        joinListed(created);
        joinListed(created);
        leagueStartUseCase.start(HOST_MEMBER_ID, created.leagueId());
        return created;
    }

    /** 리그에 입장해 상장을 마친 참여자를 추가한다. */
    private JoinedLeague joinListed(CreatedLeague created) {
        JoinedLeague joined = leagueService.join(nextMemberId++, created.inviteCode());
        leagueService.completeListing(joined.playerId());
        return joined;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
