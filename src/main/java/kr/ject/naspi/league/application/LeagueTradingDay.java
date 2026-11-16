package kr.ject.naspi.league.application;

import java.time.LocalDateTime;

/**
 * 진행 중인 리그의 현재 거래일과 운영 기간.
 *
 * @param dayNumber 현재 거래일 번호 (1~7)
 * @param totalDays 전체 거래일 수 (7)
 * @param dayEndsAt 현재 거래일이 끝나는 일시 (KST). 다음 거래일로 바뀌는 09:00이며, 마지막 거래일은 리그 종료 일시와 같다.
 * @param startedAt 리그 시작 일시 (KST)
 * @param endsAt 리그 종료 일시 (KST)
 */
public record LeagueTradingDay(int dayNumber, int totalDays, LocalDateTime dayEndsAt,
        LocalDateTime startedAt, LocalDateTime endsAt) {
}
