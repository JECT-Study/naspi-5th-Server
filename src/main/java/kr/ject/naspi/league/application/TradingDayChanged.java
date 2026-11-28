package kr.ject.naspi.league.application;

import java.util.Set;

/**
 * 거래일 전환 이벤트. 한 리그의 밀린 거래일 전환을 모두 처리한 뒤 리그당 한 번 발행한다.
 *
 * <p>websocket이 커밋 이후 그 리그의 구독자에게 거래일 갱신 알림을 보낸다 (docs/policies/v1/domain/order.md
 * "주문 알림"). 리그 종료 처리 중 밀린 전환을 보정할 때는 발행하지 않는다.
 *
 * @param leagueId 리그 ID
 * @param dayNumber 새 거래일 일차
 * @param expiredPlayerIds 전환으로 미체결 주문이 만료된 플레이어 ID
 */
public record TradingDayChanged(long leagueId, int dayNumber, Set<Long> expiredPlayerIds) {
}
