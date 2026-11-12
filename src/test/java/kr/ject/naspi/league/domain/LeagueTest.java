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

class LeagueTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 21, 30);
    private static final LocalDateTime EXPIRES_AT = CREATED_AT.plusDays(7);
    private static final LocalDateTime STARTED_AT = CREATED_AT.plusDays(1);

    @Test
    @DisplayName("대기실은 생성 시점으로부터 7일이 지나는 순간 만료된다")
    void expiresSevenDaysAfterCreation() {
        League league = newLeague();

        assertThat(league.waitingRoomExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(league.isExpired(EXPIRES_AT.minusNanos(1))).isFalse();
        assertThat(league.isExpired(EXPIRES_AT)).isTrue();
    }

    @Test
    @DisplayName("7일이 지나지 않은 대기실만 초대 코드로 입장할 수 있다")
    void openForInvitationUntilExpired() {
        League league = newLeague();

        assertThat(league.isOpenForInvitation(EXPIRES_AT.minusNanos(1))).isTrue();
        assertThat(league.isOpenForInvitation(EXPIRES_AT)).isFalse();
    }

    @Test
    @DisplayName("대기실이 만료되면 만료 상태가 되고 초대 코드가 폐기된다")
    void expire() {
        League league = newLeague();

        league.expire(EXPIRES_AT);

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.EXPIRED);
        assertThat(league.getInviteCode()).isNull();
        assertThat(league.isExpired(EXPIRES_AT)).isTrue();
        assertThat(league.isOpenForInvitation(EXPIRES_AT)).isFalse();
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

        assertThat(league.isExpired(EXPIRES_AT.plusDays(1))).isFalse();
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
    @DisplayName("리그를 시작하면 시작 상태가 되고 시작 일시가 기록되며 초대 코드가 폐기된다")
    void start() {
        League league = newLeague();

        league.start(participants(3), STARTED_AT);

        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
        assertThat(league.getStartedAt()).isEqualTo(STARTED_AT);
        assertThat(league.getInviteCode()).isNull();
        assertThat(league.isOpenForInvitation(STARTED_AT)).isFalse();
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
    @DisplayName("생성 후 7일이 지난 대기실은 시작할 수 없다")
    void startExpired() {
        League league = newLeague();

        assertThatThrownBy(() -> league.start(participants(3), EXPIRES_AT))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
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
