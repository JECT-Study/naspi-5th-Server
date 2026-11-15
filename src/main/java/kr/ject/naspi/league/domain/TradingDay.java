package kr.ject.naspi.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래일 (ERD: trading_day). 리그의 1~7일차 구간이며, 시세·주문·체결이 어느 거래일에 속하는지 가리킨다.
 *
 * <p>리그 종료 시각은 시작할 때 정해지고 바뀌지 않으므로, 리그를 시작할 때 7일치를 한 번에 만든다.
 * 구간 계산은 {@link TradingSchedule}이 맡는다.
 */
@Entity
@Table(name = "trading_day", uniqueConstraints = @UniqueConstraint(
        name = "uk_trading_day_league_day", columnNames = {"league_id", "day_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    /** 거래일 번호 (1~7). */
    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    /** 시작 일시 (포함). 1일차는 리그 시작 일시, 이후는 해당일 09:00 KST. */
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** 종료 일시 (미포함). 다음 날 09:00 KST이며, 마지막 거래일은 리그 종료 시각과 같다. */
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    private TradingDay(long leagueId, TradingSchedule.Period period) {
        this.leagueId = leagueId;
        this.dayNumber = period.dayNumber();
        this.startsAt = Objects.requireNonNull(period.startsAt(), "startsAt");
        this.endsAt = Objects.requireNonNull(period.endsAt(), "endsAt");
    }

    /**
     * 리그 운영 일정으로 1~7일차 거래일을 만든다.
     *
     * @param leagueId 리그 ID
     * @param schedule 리그 운영 일정
     * @return 일차 순서대로 정렬된 거래일
     */
    public static List<TradingDay> of(long leagueId, TradingSchedule schedule) {
        return schedule.periods().stream()
                .map(period -> new TradingDay(leagueId, period))
                .toList();
    }
}
