package kr.ject.naspi.websocket.api.response.league;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 리그 상세 메시지. 대기실 화면의 플레이어 목록과 리그 시작 가능 여부를 실시간으로 반영한다.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/detail} (리그에 입장한 회원만 구독 가능)
 *
 * <p>REST {@code GET /api/leagues/{leagueId}/detail} 응답에서 플레이어의 {@code me}만 빼고, 계산 시각
 * {@code asOf}를 더한 구조다. {@code me}는 구독자마다 다르므로 클라이언트가 REST로 받은 본인 플레이어 ID와
 * 비교한다.
 *
 * @param leagueId 리그 ID
 * @param name 리그 이름
 * @param inviteCode 대기실 초대 코드. 리그가 시작되었거나 대기실이 만료되었으면 {@code null}
 * @param waitingRoomExpiresAt 대기실 만료 일시 (KST). 대기실이 아니면 {@code null}
 * @param startable 리그 시작 가능 여부 (참여자 3명 이상, 대기자 0명)
 * @param tradingDay 현재 거래일과 리그 운영 기간. 아직 시작하지 않은 리그(대기실)는 {@code null}
 * @param players 참여일시 순 플레이어 목록
 * @param asOf 계산 시각 (KST). 표시 중인 값보다 이른 메시지는 무시한다
 */
public record LeagueDetailResponse(long leagueId, String name, String inviteCode,
        LocalDateTime waitingRoomExpiresAt, boolean startable, TradingDay tradingDay,
        List<Player> players, LocalDateTime asOf) {

    /** 메시지 종류 ({@code TopicEnvelope.type}). */
    public static final String TYPE = "LEAGUE_DETAIL";

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public LeagueDetailResponse {
        players = List.copyOf(players);
    }

    /**
     * 현재 거래일과 리그 운영 기간.
     *
     * @param dayNumber 현재 거래일 번호 (1~7)
     * @param totalDays 전체 거래일 수 (7)
     * @param dayEndsAt 현재 거래일이 끝나는 일시 (KST, 다음 09:00)
     * @param startedAt 리그 시작 일시 (KST)
     * @param endsAt 리그 종료 일시 (KST)
     */
    public record TradingDay(int dayNumber, int totalDays, LocalDateTime dayEndsAt,
            LocalDateTime startedAt, LocalDateTime endsAt) {
    }

    /**
     * 리그 플레이어.
     *
     * @param playerId 플레이어 ID
     * @param name 회원 이름. 탈퇴한 참여자면 '탈퇴한 사용자'
     * @param host 방장 여부
     * @param listed 상장 완료 여부 (true 참여자, false 대기자)
     * @param joinedAt 참여일시 (KST)
     * @param listedAt 상장일시 (KST). 대기자는 {@code null}
     * @param stock 상장한 종목. 대기자는 {@code null}
     */
    public record Player(long playerId, String name, boolean host, boolean listed,
            LocalDateTime joinedAt, LocalDateTime listedAt, Stock stock) {
    }

    /**
     * 플레이어가 상장한 종목.
     *
     * @param stockId 종목 ID
     * @param name 종목 이름
     * @param description 종목 소개
     */
    public record Stock(long stockId, String name, String description) {
    }
}
