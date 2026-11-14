package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.StockListingService;
import kr.ject.naspi.market.application.StockQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueLeaveUseCase.class, LeagueStartUseCase.class, LeagueService.class,
        LeagueCreationUseCase.class, InviteCodeIssuer.class, LeagueJoinLimit.class,
        StockListingService.class, StockQueryService.class, MutableClock.Config.class})
class LeagueLeaveUseCaseTest {

    private static final long HOST_MEMBER_ID = 1L;

    @Autowired
    private LeagueLeaveUseCase useCaseUnderTest;

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

    @Test
    @DisplayName("대기자는 리그가 시작되기 전에 대기실을 나갈 수 있다")
    void waitingPlayerLeaves() {
        CreatedLeague created = createLeague();
        long memberId = nextMemberId++;
        JoinedLeague joined = leagueService.join(memberId, created.inviteCode());

        LeftLeague left = useCaseUnderTest.leave(memberId, created.leagueId());

        assertThat(left).isEqualTo(new LeftLeague(created.leagueId(), false));
        flushAndClear();
        assertThat(entityManager.find(Player.class, joined.playerId())).isNull();
    }

    @Test
    @DisplayName("상장을 마친 참여자도 리그가 시작되기 전에 대기실을 나갈 수 있다")
    void listedPlayerLeaves() {
        CreatedLeague created = createLeague();
        long memberId = nextMemberId++;
        JoinedLeague joined = leagueService.join(memberId, created.inviteCode());
        leagueService.completeListing(joined.playerId());

        useCaseUnderTest.leave(memberId, created.leagueId());

        flushAndClear();
        assertThat(entityManager.find(Player.class, joined.playerId())).isNull();
        assertThat(entityManager.find(League.class, created.leagueId()).getStatus())
                .isEqualTo(LeagueStatus.WAITING);
    }

    // TODO: market에 종목 삭제 서비스가 생기면 "대기실을 나가면 상장된 종목은 삭제된다" 테스트를 추가한다.

    @Test
    @DisplayName("대기실을 나간 사용자는 초대 코드로 다시 입장할 수 있다")
    void rejoinAfterLeave() {
        CreatedLeague created = createLeague();
        long memberId = nextMemberId++;
        JoinedLeague joined = leagueService.join(memberId, created.inviteCode());
        useCaseUnderTest.leave(memberId, created.leagueId());
        flushAndClear();

        JoinedLeague rejoined = leagueService.join(memberId, created.inviteCode());

        assertThat(rejoined.playerId()).isNotEqualTo(joined.playerId());
    }

    @Test
    @DisplayName("방장이 대기실을 나가면 상장을 먼저 마친 참여자에게 방장이 위임된다")
    void hostLeavesAndDelegates() {
        CreatedLeague created = createLeague();
        final JoinedLeague waiting = leagueService.join(nextMemberId++, created.inviteCode());
        // 먼저 입장했지만 나중에 상장한 참여자보다, 상장을 먼저 마친 참여자가 방장이 된다.
        JoinedLeague second = leagueService.join(nextMemberId++, created.inviteCode());
        clock.advance(Duration.ofMinutes(1));
        final JoinedLeague first = join(created);
        clock.advance(Duration.ofMinutes(1));
        leagueService.completeListing(second.playerId());

        LeftLeague left = useCaseUnderTest.leave(HOST_MEMBER_ID, created.leagueId());

        assertThat(left.waitingRoomDeleted()).isFalse();
        flushAndClear();
        League league = entityManager.find(League.class, created.leagueId());
        assertThat(league.getHostPlayerId()).isEqualTo(first.playerId());
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.WAITING);
        assertThat(league.getInviteCode()).isNotNull();
        assertThat(entityManager.find(Player.class, created.hostPlayerId())).isNull();
        assertThat(entityManager.find(Player.class, waiting.playerId())).isNotNull();
    }

    @Test
    @DisplayName("방장이 나갈 때 방장을 넘겨받을 참여자가 없으면 대기실이 삭제되고 초대 코드가 폐기된다")
    void hostLeavesWithoutParticipantDeletesWaitingRoom() {
        CreatedLeague created = createLeague();
        long waitingMemberId = nextMemberId++;
        leagueService.join(waitingMemberId, created.inviteCode());

        LeftLeague left = useCaseUnderTest.leave(HOST_MEMBER_ID, created.leagueId());

        assertThat(left).isEqualTo(new LeftLeague(created.leagueId(), true));
        flushAndClear();
        League league = entityManager.find(League.class, created.leagueId());
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.DELETED);
        assertThat(league.getInviteCode()).isNull();
        assertThat(league.getHostPlayerId()).isNull();
        // 삭제된 대기실은 남은 대기자도 접근할 수 없다.
        assertLeaveFails(waitingMemberId, created, ErrorCode.LEAGUE_NOT_FOUND);
        assertThatThrownBy(() -> leagueService.join(nextMemberId++, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("리그가 시작된 이후에는 리그에서 나갈 수 없다")
    void cannotLeaveAfterStart() {
        CreatedLeague created = createLeague();
        final long memberId = nextMemberId;
        join(created);
        join(created);
        leagueStartUseCase.start(HOST_MEMBER_ID, created.leagueId());

        assertLeaveFails(memberId, created, ErrorCode.LEAGUE_ALREADY_STARTED);
        assertLeaveFails(HOST_MEMBER_ID, created, ErrorCode.LEAGUE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 나갈 수 없다")
    void outsiderCannotLeave() {
        CreatedLeague created = createLeague();

        assertLeaveFails(nextMemberId++, created, ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("생성 후 7일이 지나 만료된 대기실은 나갈 수 없다")
    void cannotLeaveExpiredWaitingRoom() {
        CreatedLeague created = createLeague();
        clock.advance(League.WAITING_ROOM_LIFETIME);

        assertLeaveFails(HOST_MEMBER_ID, created, ErrorCode.LEAGUE_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 리그는 나갈 수 없다")
    void unknownLeague() {
        assertThatThrownBy(() -> useCaseUnderTest.leave(HOST_MEMBER_ID, 999_999L))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
    }

    private void assertLeaveFails(long memberId, CreatedLeague created, ErrorCode errorCode) {
        assertThatThrownBy(() -> useCaseUnderTest.leave(memberId, created.leagueId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    private CreatedLeague createLeague() {
        return leagueCreationUseCase.create(HOST_MEMBER_ID, "리그", "나스피", "소개");
    }

    /** 리그에 입장해 상장을 마친 참여자를 추가한다. */
    private JoinedLeague join(CreatedLeague created) {
        JoinedLeague joined = leagueService.join(nextMemberId++, created.inviteCode());
        leagueService.completeListing(joined.playerId());
        return joined;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
