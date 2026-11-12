package kr.ject.naspi.league.api.info;

/**
 * 리그 간단 정보.
 *
 * @param leagueId 리그 ID
 * @param name 리그 이름
 * @param status 리그 상태 ({@code WAITING} 대기실, {@code STARTED} 진행 중, {@code ENDED} 종료)
 * @param playerCount 입장한 사용자 수 (참여자 + 대기자)
 * @param participantCount 참여자 수 (상장을 마친 플레이어 수)
 */
public record LeagueInfoResponse(long leagueId, String name, String status, long playerCount,
        long participantCount) {
}
