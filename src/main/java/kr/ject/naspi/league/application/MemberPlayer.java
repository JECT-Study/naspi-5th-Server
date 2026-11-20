package kr.ject.naspi.league.application;

/**
 * 회원이 참여한 리그의 플레이어.
 *
 * @param leagueId 리그 ID
 * @param playerId 플레이어 ID
 */
public record MemberPlayer(long leagueId, long playerId) {
}
