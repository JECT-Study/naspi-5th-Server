package kr.ject.naspi.league.application;

/**
 * 초대 코드로 입장한 리그.
 *
 * @param leagueId 리그 ID
 * @param playerId 입장한 회원의 플레이어 ID (대기자)
 */
public record JoinedLeague(long leagueId, long playerId) {
}
