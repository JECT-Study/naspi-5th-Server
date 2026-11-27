package kr.ject.naspi.league.application;

import java.time.LocalDateTime;

/**
 * 리그 종료 이벤트. websocket 알림 발행(WS-04)에 쓴다. 하위 모듈의 후속 처리는 이 이벤트가 아니라 리그 종료
 * UseCase가 직접 호출한다.
 *
 * <p>리그 종료 트랜잭션 안에서 발행된다. 구독하는 쪽은 커밋 후에 받아야 한다.
 *
 * @param leagueId 리그 ID
 * @param endedAt 리그 종료 시각 (리그의 종료 일시)
 */
public record LeagueEnded(long leagueId, LocalDateTime endedAt) {
}
