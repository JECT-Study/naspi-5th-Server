package kr.ject.naspi.order.application;

/**
 * 지정가 주문 접수 이벤트. 주문을 저장하고 매칭·정산까지 마친 뒤 발행한다.
 *
 * <p>websocket이 커밋 이후 주문한 플레이어의 자산(예약 자산, 대기 주문)과 해당 종목 호가를
 * 발행한다. 시장가 주문은 접수 트랜잭션에서 체결되거나 바로 취소되어 대기 주문으로 남지 않으므로
 * 발행하지 않는다 (체결분은 {@link TradeExecuted}가 알린다).
 *
 * @param leagueId 리그 ID
 * @param stockId 종목 ID
 * @param orderId 주문 ID
 * @param playerId 주문한 플레이어 ID
 */
public record OrderPlaced(long leagueId, long stockId, long orderId, long playerId) {
}
