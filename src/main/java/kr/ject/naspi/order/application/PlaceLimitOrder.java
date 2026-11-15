package kr.ject.naspi.order.application;

import kr.ject.naspi.order.domain.OrderSide;

/**
 * 지정가 주문 접수 요청.
 *
 * @param leagueId 리그 ID
 * @param playerId 주문하는 플레이어 ID
 * @param stockId 종목 ID
 * @param side 매매 구분
 * @param price 주문 가격 (100원 이상, 100원 단위)
 * @param quantity 주문 수량 (1주 이상)
 */
public record PlaceLimitOrder(long leagueId, long playerId, long stockId, OrderSide side,
        long price, long quantity) {
}
