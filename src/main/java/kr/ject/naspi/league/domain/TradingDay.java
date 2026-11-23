package kr.ject.naspi.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     * 마감 일시. 다음 거래일로 전환하며 이 거래일의 종가를 확정하고 미체결 주문을 만료한 시각이다.
     * 마감 전에는 비어 있다. 마지막 거래일의 마감은 리그 종료 처리가 맡는다.
     */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

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

    /**
     * 마감할 차례인 거래일을 일차 순서대로 고른다. 이미 마감한 거래일은 건너뛰고, 종료 시각이 지난 거래일을
     * 앞 일차부터 이어서 고르며 건너뛰지 않는다. 마지막 거래일은 리그 종료 처리가 마감하므로 고르지 않는다.
     *
     * <p>정책 (docs/policies/v1/domain/market.md "시장"): 매일 오전 9시 KST에 거래일이 바뀌며 직전 거래일이
     * 마감된다. 서버가 내려가 있던 동안 놓친 전환도 이 순서대로 보정한다.
     *
     * @param tradingDays 한 리그의 거래일
     * @param now 현재 일시
     * @return 마감할 거래일 (일차 순)
     */
    public static List<TradingDay> pendingClosures(List<TradingDay> tradingDays,
            LocalDateTime now) {
        List<TradingDay> pending = new ArrayList<>();
        for (TradingDay day : tradingDays.stream()
                .sorted(Comparator.comparingInt(TradingDay::getDayNumber)).toList()) {
            if (day.isLast() || !day.endedAt(now)) {
                break;
            }
            if (!day.isClosed()) {
                pending.add(day);
            }
        }
        return pending;
    }

    /**
     * 거래일을 마감했다고 기록한다.
     *
     * @param now 마감 일시
     * @throws IllegalStateException 이미 마감했거나, 아직 끝나지 않았거나, 마지막 거래일인 경우
     */
    public void close(LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        if (closedAt != null || isLast() || !endedAt(now)) {
            throw new IllegalStateException("거래일을 마감할 수 없습니다. leagueId=" + leagueId
                    + ", dayNumber=" + dayNumber + ", closedAt=" + closedAt);
        }
        this.closedAt = now;
    }

    /**
     * 이 거래일의 다음 거래일 전환을 기다리는 상태인지. 이 거래일이 끝났는데(종료 시각 이후) 아직 마감하지
     * 않았고 마지막 거래일이 아니면, 09:00 전환 처리 전이다.
     *
     * @param now 현재 일시
     * @return 전환 대기 중이면 {@code true}
     */
    public boolean awaitsChangeAt(LocalDateTime now) {
        return !isLast() && !isClosed() && endedAt(now);
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    /** 마지막(7일차) 거래일인지. */
    public boolean isLast() {
        return dayNumber == TradingSchedule.TRADING_DAYS;
    }

    private boolean endedAt(LocalDateTime now) {
        return !now.isBefore(endsAt);
    }
}
