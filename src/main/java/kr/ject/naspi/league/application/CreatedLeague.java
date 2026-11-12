package kr.ject.naspi.league.application;

/**
 * 생성된 리그.
 *
 * @param leagueId 리그 ID
 * @param hostPlayerId 방장 플레이어 ID
 * @param inviteCode 대기실 초대 코드
 */
public record CreatedLeague(long leagueId, long hostPlayerId, String inviteCode) {
}
