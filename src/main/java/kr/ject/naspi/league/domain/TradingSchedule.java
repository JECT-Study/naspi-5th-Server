package kr.ject.naspi.league.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 리그 운영 일정. 시작 일시만으로 종료 시각과 거래일 구간이 정해진다.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 시작 및 운영", market.md "시장"):
 * <ul>
 *   <li>종료 시각은 {@code 시작의 KST 날짜 + 7일, 09:00 KST}이며 바꿀 수 없다.</li>
 *   <li>거래일은 매일 09:00 KST 이상부터 다음 날 09:00 KST 미만까지이고, 09:00에 1일씩 늘어난다.</li>
 *   <li>00:00~09:00 사이에 시작하면 당일 09:00까지의 시간은 거래일로 따로 세지 않고 1일차로 본다.
 *       따라서 언제 시작하든 거래일은 1~7일차다.</li>
 * </ul>
 *
 * <p>일시는 모두 KST 기준 {@link LocalDateTime}이다.
 *
 * @param startedAt 리그 시작 일시 (KST)
 */
public record TradingSchedule(LocalDateTime startedAt) {

    /** 리그 운영 일수이자 거래일 수. */
    public static final int TRADING_DAYS = 7;

    /** 거래일이 바뀌는 시각 (KST). */
    public static final LocalTime DAY_START = LocalTime.of(9, 0);

    /**
     * 시작 일시로 운영 일정을 만든다.
     *
     * @param startedAt 리그 시작 일시 (KST)
     */
    public TradingSchedule {
        Objects.requireNonNull(startedAt, "startedAt");
    }

    /**
     * 리그 종료 시각. 이 시각부터 주문 접수와 체결이 끝난다.
     *
     * @return 시작의 KST 날짜 + 7일, 09:00
     */
    public LocalDateTime endsAt() {
        return startDate().plusDays(TRADING_DAYS).atTime(DAY_START);
    }

    /**
     * 주어진 시각이 몇 거래일차인지 계산한다. 시작 전 시각은 1일차, 종료 이후 시각은 7일차로 본다.
     *
     * @param at 확인할 일시 (KST)
     * @return 1~7 사이의 거래일 번호
     */
    public int dayNumberAt(LocalDateTime at) {
        LocalDate tradingDate = at.toLocalTime().isBefore(DAY_START)
                ? at.toLocalDate().minusDays(1)
                : at.toLocalDate();
        long dayNumber = ChronoUnit.DAYS.between(startDate(), tradingDate) + 1;
        return (int) Math.clamp(dayNumber, 1, TRADING_DAYS);
    }

    /**
     * 1일차부터 7일차까지의 거래일 구간. 1일차는 시작 일시부터 시작하고, 마지막 거래일은 종료 시각에 끝난다.
     *
     * @return 일차 순서대로 정렬된 거래일 구간
     */
    public List<Period> periods() {
        return IntStream.rangeClosed(1, TRADING_DAYS)
                .mapToObj(dayNumber -> new Period(dayNumber,
                        dayNumber == 1 ? startedAt : dayStart(dayNumber),
                        dayStart(dayNumber + 1)))
                .toList();
    }

    /** n일차가 시작하는 09:00. 1일차의 09:00은 시작 날짜의 09:00이다. */
    private LocalDateTime dayStart(int dayNumber) {
        return startDate().plusDays(dayNumber - 1L).atTime(DAY_START);
    }

    private LocalDate startDate() {
        return startedAt.toLocalDate();
    }

    /**
     * 한 거래일의 구간.
     *
     * @param dayNumber 거래일 번호 (1~7)
     * @param startsAt 시작 일시 (포함)
     * @param endsAt 종료 일시 (미포함)
     */
    public record Period(int dayNumber, LocalDateTime startsAt, LocalDateTime endsAt) {
    }
}
