package kr.ject.naspi.league.application;

import java.time.LocalDateTime;

/**
 * 시작한 리그.
 *
 * @param leagueId 리그 ID
 * @param startedAt 시작 일시 (KST)
 * @param endsAt 종료 일시 (KST). 시작의 KST 날짜 + 7일, 09:00이며 바뀌지 않는다.
 */
public record StartedLeague(long leagueId, LocalDateTime startedAt, LocalDateTime endsAt) {
}
