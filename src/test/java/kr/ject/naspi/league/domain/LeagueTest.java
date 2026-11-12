package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LeagueTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 21, 30);
    private static final LocalDateTime EXPIRES_AT = CREATED_AT.plusDays(7);

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
        // TODO: 리그 시작(LEA-09) 구현 후 도메인 메서드로 바꾼다
        ReflectionTestUtils.setField(league, "status", LeagueStatus.STARTED);

        assertThat(league.isExpired(EXPIRES_AT.plusDays(1))).isFalse();
        assertThatThrownBy(() -> league.expire(EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    private static League newLeague() {
        return League.create(LeagueName.of("리그"), InviteCode.of("ABC1234"), CREATED_AT);
    }
}
