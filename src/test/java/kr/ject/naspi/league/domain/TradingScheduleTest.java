package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.league.domain.TradingSchedule.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TradingScheduleTest {

    /** 2026-11-16 (월) 15:00 KST 시작. */
    private static final LocalDateTime AFTERNOON_START = LocalDateTime.of(2026, 11, 16, 15, 0);

    /** 2026-11-16 (월) 03:00 KST 시작. */
    private static final LocalDateTime DAWN_START = LocalDateTime.of(2026, 11, 16, 3, 0);

    @ParameterizedTest(name = "{0} 시작 → {1} 종료")
    @CsvSource({
        "2026-11-16T15:00:00, 2026-11-23T09:00:00",
        "2026-11-16T03:00:00, 2026-11-23T09:00:00",
        "2026-11-16T00:00:00, 2026-11-23T09:00:00",
        "2026-11-16T08:59:59, 2026-11-23T09:00:00",
        "2026-11-16T09:00:00, 2026-11-23T09:00:00",
        "2026-11-16T23:59:59, 2026-11-23T09:00:00",
    })
    @DisplayName("종료 시각은 시작의 KST 날짜 + 7일, 09:00 KST이다")
    void endsAt(LocalDateTime startedAt, LocalDateTime expected) {
        assertThat(new TradingSchedule(startedAt).endsAt()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}일차")
    @CsvSource({
        "2026-11-16T15:00:00, 1",
        "2026-11-17T08:59:59, 1",
        "2026-11-17T09:00:00, 2",
        "2026-11-22T08:59:59, 6",
        "2026-11-22T09:00:00, 7",
        "2026-11-23T08:59:59, 7",
    })
    @DisplayName("거래일은 09:00 KST 이상부터 다음 날 09:00 KST 미만까지이며, 매일 09:00에 1일씩 늘어난다")
    void dayNumberAt(LocalDateTime at, int expected) {
        assertThat(new TradingSchedule(AFTERNOON_START).dayNumberAt(at)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}일차")
    @CsvSource({
        "2026-11-16T03:00:00, 1",
        "2026-11-16T08:59:59, 1",
        "2026-11-16T09:00:00, 1",
        "2026-11-17T08:59:59, 1",
        "2026-11-17T09:00:00, 2",
        "2026-11-22T09:00:00, 7",
        "2026-11-23T08:59:59, 7",
    })
    @DisplayName("00:00~09:00에 시작하면 당일 09:00까지는 거래일로 따로 세지 않고 1일차로 표시한다")
    void dawnStartDayNumber(LocalDateTime at, int expected) {
        assertThat(new TradingSchedule(DAWN_START).dayNumberAt(at)).isEqualTo(expected);
    }

    @Test
    @DisplayName("거래일은 1~7일차이고, 1일차는 시작 일시부터 마지막 거래일은 종료 시각까지다")
    void periods() {
        List<Period> periods = new TradingSchedule(AFTERNOON_START).periods();

        assertThat(periods).hasSize(TradingSchedule.TRADING_DAYS);
        assertThat(periods.getFirst()).isEqualTo(new Period(1,
                AFTERNOON_START, LocalDateTime.of(2026, 11, 17, 9, 0)));
        assertThat(periods.get(1)).isEqualTo(new Period(2,
                LocalDateTime.of(2026, 11, 17, 9, 0), LocalDateTime.of(2026, 11, 18, 9, 0)));
        assertThat(periods.getLast()).isEqualTo(new Period(7,
                LocalDateTime.of(2026, 11, 22, 9, 0), LocalDateTime.of(2026, 11, 23, 9, 0)));
        // 구간은 빈틈 없이 이어진다.
        for (int i = 1; i < periods.size(); i++) {
            assertThat(periods.get(i).startsAt()).isEqualTo(periods.get(i - 1).endsAt());
        }
    }

    @Test
    @DisplayName("00:00~09:00에 시작하면 1일차는 시작 일시부터 다음 날 09:00까지다")
    void dawnStartPeriods() {
        List<Period> periods = new TradingSchedule(DAWN_START).periods();

        assertThat(periods).hasSize(TradingSchedule.TRADING_DAYS);
        assertThat(periods.getFirst()).isEqualTo(new Period(1,
                DAWN_START, LocalDateTime.of(2026, 11, 17, 9, 0)));
        assertThat(periods.getLast().endsAt()).isEqualTo(LocalDateTime.of(2026, 11, 23, 9, 0));
    }
}
