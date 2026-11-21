package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TradingDayTest {

    /** 2026-11-15 10:00 시작: 2일차는 11-16 09:00, 7일차는 11-21 09:00 ~ 11-22 09:00(종료). */
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Test
    @DisplayName("매일 09:00 KST에 직전 거래일이 마감 대상이 된다 (08:59:59는 아직 아니다)")
    void closableAtNine() {
        List<TradingDay> days = days(STARTED_AT);

        assertThat(TradingDay.pendingClosures(days, DAY2.minusSeconds(1))).isEmpty();
        assertThat(TradingDay.pendingClosures(days, DAY2))
                .extracting(TradingDay::getDayNumber).containsExactly(1);
    }

    @Test
    @DisplayName("00:00~09:00에 시작한 리그는 당일 09:00에 전환하지 않고 다음 날 09:00에 1일차를 마감한다")
    void dawnStartHasNoChangeSameDay() {
        LocalDateTime dawn = LocalDateTime.of(2026, 11, 2, 3, 0);
        List<TradingDay> days = days(dawn);

        assertThat(TradingDay.pendingClosures(days, dawn.toLocalDate().atTime(9, 0))).isEmpty();
        assertThat(TradingDay.pendingClosures(days, dawn.toLocalDate().plusDays(1).atTime(9, 0)))
                .extracting(TradingDay::getDayNumber).containsExactly(1);
    }

    @Test
    @DisplayName("서버가 내려가 놓친 거래일 전환은 일차 순서대로 모두 마감 대상이 된다")
    void pendingClosuresAfterDowntime() {
        List<TradingDay> days = days(STARTED_AT);
        days.get(0).close(DAY2);

        assertThat(TradingDay.pendingClosures(days, DAY2.plusDays(3).plusHours(1)))
                .extracting(TradingDay::getDayNumber).containsExactly(2, 3, 4);
    }

    @Test
    @DisplayName("마지막 거래일(7일차)은 전환 마감 대상에서 제외한다 (리그 종료 처리가 마감한다)")
    void lastDayIsExcluded() {
        List<TradingDay> days = days(STARTED_AT);
        LocalDateTime afterEnd = days.get(6).getEndsAt().plusDays(1);

        assertThat(TradingDay.pendingClosures(days, afterEnd))
                .extracting(TradingDay::getDayNumber).containsExactly(1, 2, 3, 4, 5, 6);
        assertThatThrownBy(() -> days.get(6).close(afterEnd))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("앞 일차가 마감되지 않았으면 뒤 일차를 건너뛰고 마감하지 않는다")
    void doesNotSkipDays() {
        List<TradingDay> days = days(STARTED_AT);

        assertThat(TradingDay.pendingClosures(days, DAY2.plusDays(1).plusSeconds(1)))
                .extracting(TradingDay::getDayNumber).containsExactly(1, 2);
        assertThat(TradingDay.pendingClosures(days, DAY2.minusSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("이미 마감한 거래일은 다시 마감하지 않는다")
    void closedDayIsNotClosedAgain() {
        TradingDay day1 = days(STARTED_AT).get(0);
        day1.close(DAY2);

        assertThat(day1.getClosedAt()).isEqualTo(DAY2);
        assertThat(TradingDay.pendingClosures(List.of(day1), DAY2.plusDays(1))).isEmpty();
        assertThatThrownBy(() -> day1.close(DAY2.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("끝나지 않은 거래일은 마감할 수 없다")
    void cannotCloseBeforeEnd() {
        TradingDay day1 = days(STARTED_AT).get(0);

        assertThatThrownBy(() -> day1.close(DAY2.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(day1.isClosed()).isFalse();
    }

    private static List<TradingDay> days(LocalDateTime startedAt) {
        return TradingDay.of(1L, new TradingSchedule(startedAt));
    }
}
