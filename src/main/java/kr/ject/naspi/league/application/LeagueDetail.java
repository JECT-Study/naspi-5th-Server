package kr.ject.naspi.league.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리그 상세 정보. 플레이어 목록과 각 참여자의 종목, 리그 시작 가능 여부, 현재 거래일.
 *
 * @param leagueId 리그 ID
 * @param name 리그 이름
 * @param inviteCode 대기실 초대 코드. 리그가 시작되었거나 대기실이 만료되었으면 {@code null}
 * @param waitingRoomExpiresAt 대기실 만료 일시. 대기실이 아니면 {@code null}
 * @param startable 리그 시작 가능 여부 (참여자 3명 이상, 대기자 0명)
 * @param tradingDay 현재 거래일과 리그 운영 기간. 아직 시작하지 않은 리그(대기실)는 {@code null}
 * @param players 참여일시 순 플레이어 목록
 */
public record LeagueDetail(long leagueId, String name, String inviteCode,
        LocalDateTime waitingRoomExpiresAt, boolean startable, LeagueTradingDay tradingDay,
        List<DetailPlayer> players) {

    /**
     * 리그 플레이어와 그의 종목.
     *
     * @param playerId 플레이어 ID
     * @param name 회원 이름. 탈퇴한 참여자면 '탈퇴한 사용자'
     * @param me 조회하는 회원 본인 여부
     * @param host 방장 여부
     * @param listed 상장 완료 여부 (true 참여자, false 대기자)
     * @param joinedAt 참여일시
     * @param listedAt 상장일시. 대기자는 {@code null}
     * @param stock 상장한 종목. 대기자는 {@code null}
     */
    public record DetailPlayer(long playerId, String name, boolean me, boolean host, boolean listed,
            LocalDateTime joinedAt, LocalDateTime listedAt, PlayerStock stock) {
    }

    /**
     * 플레이어가 상장한 종목.
     *
     * @param stockId 종목 ID
     * @param name 종목 이름
     * @param description 종목 소개
     */
    public record PlayerStock(long stockId, String name, String description) {
    }
}
