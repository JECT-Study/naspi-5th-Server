package kr.ject.naspi.league.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리그에 입장한 플레이어 목록과 리그 시작 가능 여부.
 *
 * @param leagueId 리그 ID
 * @param startable 리그 시작 가능 여부 (참여자 3명 이상, 대기자 0명)
 * @param players 참여일시 순 플레이어 목록
 */
public record LeaguePlayers(long leagueId, boolean startable, List<LeaguePlayer> players) {

    /**
     * 리그 플레이어.
     *
     * @param playerId 플레이어 ID
     * @param name 회원 이름. 탈퇴한 회원이면 {@code null}
     * @param host 방장 여부
     * @param listed 상장 완료 여부 (true 참여자, false 대기자)
     * @param joinedAt 참여일시
     * @param listedAt 상장일시. 대기자는 {@code null}
     */
    public record LeaguePlayer(long playerId, String name, boolean host, boolean listed,
            LocalDateTime joinedAt, LocalDateTime listedAt) {
    }
}
