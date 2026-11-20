package kr.ject.naspi.league.application;

import java.time.LocalDateTime;
import kr.ject.naspi.league.domain.LeagueStatus;

/**
 * 회원이 입장한 리그 목록의 한 항목.
 *
 * @param leagueId 리그 ID
 * @param name 리그 이름
 * @param status 저장된 리그 상태 (대기실, 진행 중, 종료)
 * @param playerCount 입장 인원 (참여자 + 대기자)
 * @param participantCount 참여자 수 (상장을 마친 플레이어)
 * @param host 회원이 방장인지 여부
 * @param listed 회원의 상장 완료 여부 (false면 대기자)
 * @param joinedAt 회원의 입장 일시 (KST)
 * @param createdAt 리그 생성 일시 (KST)
 * @param waitingRoomExpiresAt 대기실 만료 일시 (KST). 대기실이 아니면 {@code null}
 * @param startedAt 리그 시작 일시 (KST). 시작 전이면 {@code null}
 * @param endsAt 리그 종료 일시 (KST). 시작 전이면 {@code null}
 * @param tradingDay 현재 거래일. 진행 중이 아니면 {@code null}
 */
public record MyLeague(long leagueId, String name, LeagueStatus status, long playerCount,
        long participantCount, boolean host, boolean listed, LocalDateTime joinedAt,
        LocalDateTime createdAt, LocalDateTime waitingRoomExpiresAt, LocalDateTime startedAt,
        LocalDateTime endsAt, LeagueTradingDay tradingDay) {
}
