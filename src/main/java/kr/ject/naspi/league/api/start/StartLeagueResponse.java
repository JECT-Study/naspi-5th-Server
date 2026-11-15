package kr.ject.naspi.league.api.start;

import java.time.LocalDateTime;
import kr.ject.naspi.league.application.StartedLeague;

/**
 * 리그 시작 결과.
 *
 * @param leagueId 시작한 리그 ID
 * @param startedAt 시작 일시 (KST)
 * @param endsAt 종료 일시 (KST). 시작의 KST 날짜 + 7일, 09:00이며 바뀌지 않는다.
 */
public record StartLeagueResponse(long leagueId, LocalDateTime startedAt, LocalDateTime endsAt) {

    static StartLeagueResponse from(StartedLeague started) {
        return new StartLeagueResponse(started.leagueId(), started.startedAt(), started.endsAt());
    }
}
