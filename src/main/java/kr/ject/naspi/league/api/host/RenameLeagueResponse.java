package kr.ject.naspi.league.api.host;

import kr.ject.naspi.league.application.RenamedLeague;

/**
 * 리그 이름 수정 결과.
 *
 * @param leagueId 리그 ID
 * @param name 바뀐 리그 이름
 */
public record RenameLeagueResponse(long leagueId, String name) {

    static RenameLeagueResponse from(RenamedLeague renamed) {
        return new RenameLeagueResponse(renamed.leagueId(), renamed.name());
    }
}
