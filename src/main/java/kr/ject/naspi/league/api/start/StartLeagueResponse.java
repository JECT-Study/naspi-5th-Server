package kr.ject.naspi.league.api.start;

import java.time.LocalDateTime;
import kr.ject.naspi.league.application.StartedLeague;

/**
 * 리그 시작 결과.
 *
 * @param leagueId 시작한 리그 ID
 * @param startedAt 시작 일시 (KST)
 */
public record StartLeagueResponse(long leagueId, LocalDateTime startedAt) {

    static StartLeagueResponse from(StartedLeague started) {
        return new StartLeagueResponse(started.leagueId(), started.startedAt());
    }
}
