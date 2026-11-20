package kr.ject.naspi.league.api.mine;

import java.time.LocalDateTime;
import kr.ject.naspi.league.application.LeagueTradingDay;
import kr.ject.naspi.league.application.MyLeague;

/**
 * 내가 입장한 리그 목록의 한 항목.
 *
 * @param leagueId 리그 ID
 * @param name 리그 이름
 * @param status 리그 상태 ({@code WAITING}, {@code STARTED}, {@code ENDED})
 * @param playerCount 입장 인원 (참여자 + 대기자)
 * @param participantCount 참여자 수 (상장을 마친 플레이어)
 * @param host 내가 방장인지 여부
 * @param listed 내 상장 완료 여부 (false면 대기자)
 * @param joinedAt 내 입장 일시 (KST)
 * @param createdAt 리그 생성 일시 (KST)
 * @param waitingRoomExpiresAt 대기실 만료 일시 (KST). 대기실이 아니면 {@code null}
 * @param startedAt 리그 시작 일시 (KST). 시작 전이면 {@code null}
 * @param endsAt 리그 종료 일시 (KST). 시작 전이면 {@code null}
 * @param tradingDay 현재 거래일. 진행 중이 아니면 {@code null}
 */
public record MyLeagueResponse(long leagueId, String name, String status, long playerCount,
        long participantCount, boolean host, boolean listed, LocalDateTime joinedAt,
        LocalDateTime createdAt, LocalDateTime waitingRoomExpiresAt, LocalDateTime startedAt,
        LocalDateTime endsAt, TradingDayResponse tradingDay) {

    static MyLeagueResponse from(MyLeague league) {
        return new MyLeagueResponse(league.leagueId(), league.name(), league.status().name(),
                league.playerCount(), league.participantCount(), league.host(), league.listed(),
                league.joinedAt(), league.createdAt(), league.waitingRoomExpiresAt(),
                league.startedAt(), league.endsAt(), TradingDayResponse.from(league.tradingDay()));
    }

    /**
     * 현재 거래일.
     *
     * @param dayNumber 현재 거래일 번호 (1~7)
     * @param totalDays 전체 거래일 수 (7)
     * @param dayEndsAt 현재 거래일이 끝나는 일시 (KST, 다음 09:00)
     */
    public record TradingDayResponse(int dayNumber, int totalDays, LocalDateTime dayEndsAt) {

        static TradingDayResponse from(LeagueTradingDay tradingDay) {
            if (tradingDay == null) {
                return null;
            }
            return new TradingDayResponse(tradingDay.dayNumber(), tradingDay.totalDays(),
                    tradingDay.dayEndsAt());
        }
    }
}
