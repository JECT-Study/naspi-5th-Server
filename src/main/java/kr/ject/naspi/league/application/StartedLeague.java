package kr.ject.naspi.league.application;

import java.time.LocalDateTime;

/**
 * 시작한 리그.
 *
 * @param leagueId 리그 ID
 * @param startedAt 시작 일시 (KST)
 */
public record StartedLeague(long leagueId, LocalDateTime startedAt) {
}
