package kr.ject.naspi.league.application;

/**
 * 리그 이름 수정 결과.
 *
 * @param leagueId 리그 ID
 * @param name 바뀐 리그 이름
 */
public record RenamedLeague(long leagueId, String name) {
}
