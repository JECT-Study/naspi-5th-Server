package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LeagueTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 21, 30);
    private static final LocalDateTime EXPIRES_AT = CREATED_AT.plusDays(7);
    private static final LocalDateTime STARTED_AT = CREATED_AT.plusDays(1);

    @Test
    @DisplayName("대기실의 만료 기한은 생성 시점으로부터 7일 뒤다")
    void expiresSevenDaysAfterCreation() {
        League league = newLeague();

        assertThat(league.waitingRoomExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("만료 기한이 지났어도 만료 처리 전인 대기실은 만료되지 않고 초대 코드로 입장할 수 있다")
    void openForInvitationUntilExpired() {
        League league = newLeague();

        assertThat(league.isExpired()).isFalse();
        assertThat(league.isUnavailable()).isFalse();
        assertThat(league.isOpenForInvitation()).isTrue();
    }

    @Test
    @DisplayName("대기실이 만료되면 만료 상태가 되고 초대 코드가 폐기된다")
    void expire() {
        League league = newLeague();

        league.expire(EXPIRES_AT);

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.EXPIRED);
        assertThat(league.getInviteCode()).isNull();
        assertThat(league.isExpired()).isTrue();
        assertThat(league.isUnavailable()).isTrue();
        assertThat(league.isOpenForInvitation()).isFalse();
    }

    @Test
    @DisplayName("생성 후 7일이 지나지 않은 대기실은 만료시킬 수 없다")
    void cannotExpireBeforeDeadline() {
        League league = newLeague();

        assertThatThrownBy(() -> league.expire(EXPIRES_AT.minusNanos(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.WAITING);
    }

    @Test
    @DisplayName("7일 안에 시작된 리그는 만료되지 않는다")
    void startedLeagueNeverExpires() {
        League league = newLeague();
        league.start(participants(3), STARTED_AT);

        assertThat(league.isExpired()).isFalse();
        assertThatThrownBy(() -> league.expire(EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("상장을 마친 참여자가 3명 이상이고 대기자가 없으면 리그를 시작할 수 있다")
    void canStartWithThreeParticipants() {
        assertThat(League.canStart(participants(3))).isTrue();
        assertThat(League.canStart(participants(League.MAX_PLAYERS))).isTrue();
    }

    @Test
    @DisplayName("상장을 마친 참여자가 3명 미만이면 리그를 시작할 수 없다")
    void cannotStartWithTooFewParticipants() {
        assertThat(League.canStart(participants(2))).isFalse();
    }

    @Test
    @DisplayName("대기자가 1명이라도 있으면 리그를 시작할 수 없다")
    void cannotStartWithWaitingPlayer() {
        List<Player> players = participants(3);
        players.add(Player.enter(1L, 99L, CREATED_AT));

        assertThat(League.canStart(players)).isFalse();
    }

    @Test
    @DisplayName("참여자가 10명을 넘으면 리그를 시작할 수 없다")
    void cannotStartOverMaxPlayers() {
        assertThat(League.canStart(participants(League.MAX_PLAYERS + 1))).isFalse();
    }

    @Test
    @DisplayName("리그를 시작하면 시작 상태가 되고 시작 일시와 종료 일시(시작 KST 날짜 + 7일, 09:00)가 기록되며 초대 코드는 유지된다")
    void start() {
        League league = newLeague();

        league.start(participants(3), STARTED_AT);

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
        assertThat(league.getStartedAt()).isEqualTo(STARTED_AT);
        // 2026-11-02 21:30 시작 → 2026-11-09 09:00 종료
        assertThat(league.getEndsAt()).isEqualTo(LocalDateTime.of(2026, 11, 9, 9, 0));
        assertThat(league.getInviteCode()).isNotNull();
        assertThat(league.isOpenForInvitation()).isFalse();
    }

    @Test
    @DisplayName("시작 조건을 만족하지 않으면 리그를 시작할 수 없다")
    void startNotStartable() {
        League league = newLeague();

        assertThatThrownBy(() -> league.start(participants(2), STARTED_AT))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_STARTABLE);
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.WAITING);
        assertThat(league.getInviteCode()).isNotNull();
    }

    @Test
    @DisplayName("이미 시작된 리그는 다시 시작할 수 없다")
    void startTwice() {
        League league = newLeague();
        league.start(participants(3), STARTED_AT);

        assertThatThrownBy(() -> league.start(participants(3), STARTED_AT.plusHours(1)))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_STARTED);
        assertThat(league.getStartedAt()).isEqualTo(STARTED_AT);
    }

    @Test
    @DisplayName("생성 후 7일이 지났어도 만료 처리 전인 대기실은 시작할 수 있다")
    void startOverdueBeforeExpiration() {
        League league = newLeague();

        league.start(participants(3), EXPIRES_AT);

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
    }

    @Test
    @DisplayName("만료 처리된 대기실은 시작할 수 없다")
    void startExpired() {
        League league = newLeague();
        league.expire(EXPIRES_AT);

        assertThatThrownBy(() -> league.start(participants(3), EXPIRES_AT))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
    }

    @Test
    @DisplayName("대기실·진행 중 리그는 통과하고, 만료·삭제된 대기실은 리그 없음, 종료된 리그는 거절한다")
    void requireOngoing() {
        League waiting = newLeague();
        waiting.requireOngoing(EXPIRES_AT);

        League expired = newLeague();
        expired.expire(EXPIRES_AT);
        assertThatThrownBy(() -> expired.requireOngoing(EXPIRES_AT))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);

        League deleted = newLeague();
        ReflectionTestUtils.setField(deleted, "status", LeagueStatus.DELETED);
        assertThatThrownBy(() -> deleted.requireOngoing(CREATED_AT))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);

        League started = newLeague();
        started.start(participants(3), STARTED_AT);
        started.requireOngoing(started.getEndsAt().minusNanos(1));
        assertThatThrownBy(() -> started.requireOngoing(started.getEndsAt()))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED);

        League ended = newLeague();
        ReflectionTestUtils.setField(ended, "status", LeagueStatus.ENDED);
        assertThatThrownBy(() -> ended.requireOngoing(STARTED_AT))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("진행 중인 리그는 종료 시각 직전까지 거래할 수 있고, 종료 시각 09:00 정각부터는 이미 종료된 리그로 거절한다")
    void requireTradable() {
        League started = newLeague();
        started.start(participants(3), STARTED_AT);

        started.requireTradable(started.getEndsAt().minusNanos(1));
        assertThatThrownBy(() -> started.requireTradable(started.getEndsAt()))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("종료 상태 리그는 종료 시각 전이라도 거래할 수 없다")
    void requireTradableEnded() {
        League ended = newLeague();
        ReflectionTestUtils.setField(ended, "status", LeagueStatus.ENDED);

        assertThatThrownBy(() -> ended.requireTradable(STARTED_AT))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("시작하지 않은 대기실에서는 거래할 수 없다")
    void requireTradableWaiting() {
        League waiting = newLeague();

        assertThatThrownBy(() -> waiting.requireTradable(EXPIRES_AT.minusNanos(1)))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE);
    }

    @Test
    @DisplayName("만료·삭제된 대기실은 없는 리그로 거절한다")
    void requireTradableUnavailable() {
        League expired = newLeague();
        ReflectionTestUtils.setField(expired, "status", LeagueStatus.EXPIRED);
        assertThatThrownBy(() -> expired.requireTradable(CREATED_AT))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);

        League deleted = newLeague();
        ReflectionTestUtils.setField(deleted, "status", LeagueStatus.DELETED);
        assertThatThrownBy(() -> deleted.requireTradable(CREATED_AT))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
    }

    @Test
    @DisplayName("리그는 종료 시각(시작 KST 날짜 + 7일 09:00)에 종료된다")
    void endAtEndsAt() {
        League league = newLeague();
        league.start(participants(3), STARTED_AT);
        LocalDateTime endsAt = league.getEndsAt();

        assertThat(endsAt).isEqualTo(STARTED_AT.toLocalDate().plusDays(7).atTime(9, 0));
        assertThat(league.isEndDue(endsAt.minusNanos(1))).isFalse();
        assertThat(league.isEndDue(endsAt)).isTrue();

        league.end(endsAt);

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.ENDED);
        assertThat(league.isEndDue(endsAt.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("종료 시각 전에는 리그를 종료할 수 없다 (조기 종료 불가)")
    void endBeforeEndsAtRejected() {
        League league = newLeague();
        league.start(participants(3), STARTED_AT);

        assertThatThrownBy(() -> league.end(league.getEndsAt().minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
    }

    @Test
    @DisplayName("진행 중인 리그만 종료할 수 있다")
    void endOnlyStarted() {
        LocalDateTime later = STARTED_AT.plusDays(30);
        League waiting = newLeague();
        assertThatThrownBy(() -> waiting.end(later)).isInstanceOf(IllegalStateException.class);

        League expired = newLeague();
        expired.expire(EXPIRES_AT);
        assertThatThrownBy(() -> expired.end(later)).isInstanceOf(IllegalStateException.class);

        League ended = newLeague();
        ended.start(participants(3), STARTED_AT);
        ended.end(ended.getEndsAt());
        assertThatThrownBy(() -> ended.end(later)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("리그가 종료되면 초대 코드가 폐기된다")
    void endClosesInvitation() {
        League league = newLeague();
        league.start(participants(3), STARTED_AT);
        assertThat(league.getInviteCode()).isNotNull();

        league.end(league.getEndsAt());

        assertThat(league.getInviteCode()).isNull();
    }

    @Test
    @DisplayName("종료 시각부터는 종료 처리 전이라도 거래할 수 없는 리그로 본다")
    void isTradableUntilEndsAt() {
        League waiting = newLeague();
        assertThat(waiting.isTradable(STARTED_AT)).isFalse();

        League started = newLeague();
        started.start(participants(3), STARTED_AT);
        assertThat(started.isTradable(started.getEndsAt().minusNanos(1))).isTrue();
        assertThat(started.isTradable(started.getEndsAt())).isFalse();
    }

    private static League newLeague() {
        return League.create(LeagueName.of("리그"), InviteCode.of("ABC1234"), CREATED_AT);
    }

    /** 상장을 마친 참여자 count명. */
    private static List<Player> participants(int count) {
        List<Player> players = new ArrayList<>();
        for (long memberId = 1; memberId <= count; memberId++) {
            Player player = Player.enter(1L, memberId, CREATED_AT);
            player.completeListing(CREATED_AT);
            players.add(player);
        }
        return players;
    }
}
