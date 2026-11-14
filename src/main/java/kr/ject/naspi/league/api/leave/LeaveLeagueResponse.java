package kr.ject.naspi.league.api.leave;

import kr.ject.naspi.league.application.LeftLeague;

/**
 * 대기실 나가기 결과.
 *
 * @param leagueId 나간 리그 ID
 * @param waitingRoomDeleted 방장이 나갔는데 넘겨받을 참여자가 없어 대기실이 삭제되었는지 여부
 */
public record LeaveLeagueResponse(long leagueId, boolean waitingRoomDeleted) {

    static LeaveLeagueResponse from(LeftLeague left) {
        return new LeaveLeagueResponse(left.leagueId(), left.waitingRoomDeleted());
    }
}
