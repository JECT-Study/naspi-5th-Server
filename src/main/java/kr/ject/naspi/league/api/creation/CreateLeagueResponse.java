package kr.ject.naspi.league.api.creation;

/**
 * 리그 생성 결과.
 *
 * @param leagueId 생성된 리그 ID
 * @param inviteCode 다른 사용자에게 공유할 대기실 초대 코드 (영문 대문자·숫자 7자리)
 */
public record CreateLeagueResponse(long leagueId, String inviteCode) {
}
