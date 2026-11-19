package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerTest {

    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 11, 23, 9, 0);

    private Player player() {
        return Player.enter(1L, 10L, LocalDateTime.of(2026, 11, 16, 12, 0));
    }

    @Test
    @DisplayName("시작된 리그의 플레이어가 탈퇴하면 탈퇴 일시가 기록된다")
    void recordsWithdrawnAt() {
        Player player = player();
        LocalDateTime at = LocalDateTime.of(2026, 11, 18, 10, 0);

        player.withdraw(at);

        assertThat(player.isWithdrawn()).isTrue();
        assertThat(player.getWithdrawnAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("탈퇴하지 않은 플레이어는 탈퇴 일시가 없고 종료 전 탈퇴로 판정되지 않는다")
    void notWithdrawnByDefault() {
        Player player = player();

        assertThat(player.isWithdrawn()).isFalse();
        assertThat(player.isWithdrawnBefore(ENDS_AT)).isFalse();
    }

    @Test
    @DisplayName("리그 종료 시각 전에 탈퇴하면 종료 전 탈퇴로 판정한다")
    void withdrawnBeforeEnd() {
        Player player = player();
        player.withdraw(ENDS_AT.minusSeconds(1));

        assertThat(player.isWithdrawnBefore(ENDS_AT)).isTrue();
    }

    @Test
    @DisplayName("리그 종료 시각 이후(종료 시각 포함)에 탈퇴하면 종료 후 탈퇴로 판정한다")
    void withdrawnAtOrAfterEnd() {
        Player atEnd = player();
        atEnd.withdraw(ENDS_AT);
        Player afterEnd = player();
        afterEnd.withdraw(ENDS_AT.plusSeconds(1));

        assertThat(atEnd.isWithdrawnBefore(ENDS_AT)).isFalse();
        assertThat(afterEnd.isWithdrawnBefore(ENDS_AT)).isFalse();
    }

    @Test
    @DisplayName("이미 탈퇴 일시가 기록된 플레이어는 다시 기록할 수 없다")
    void cannotWithdrawTwice() {
        Player player = player();
        player.withdraw(ENDS_AT);

        assertThatThrownBy(() -> player.withdraw(ENDS_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
