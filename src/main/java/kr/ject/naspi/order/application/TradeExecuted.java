package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import kr.ject.naspi.order.domain.Execution;
import kr.ject.naspi.order.domain.OrderSide;

/**
 * 체결 이벤트. 체결 1건이 정산된 뒤 발행한다 (docs/policies/README.md 이벤트 표).
 *
 * <p>websocket이 커밋 이후 매수자·매도자의 자산과 해당 종목 호가를 발행하고, 대기 주문 주인에게 주문 체결
 * 알림을 보낸다. 대기 주문 하나는 신규 주문 하나와 한 번만 체결되므로 체결 1건이 체결된 대기 주문 1건이다.
 * 현재가·거래량 갱신은 이벤트가 아니라 체결 흐름이 market을 직접 호출한다 (docs/policies/README.md).
 *
 * @param leagueId 리그 ID
 * @param stockId 종목 ID
 * @param buyOrderId 매수 주문 ID
 * @param sellOrderId 매도 주문 ID
 * @param buyerPlayerId 매수자 플레이어 ID
 * @param sellerPlayerId 매도자 플레이어 ID
 * @param price 체결가 (원)
 * @param quantity 체결 수량 (주)
 * @param executedAt 체결 일시
 * @param buyResting 먼저 접수되어 대기 중이던 주문이 매수 주문이면 true, 매도 주문이면 false
 */
public record TradeExecuted(long leagueId, long stockId, long buyOrderId, long sellOrderId,
        long buyerPlayerId, long sellerPlayerId, long price, long quantity,
        LocalDateTime executedAt, boolean buyResting) {

    /**
     * 매칭에서 나온 체결로 이벤트를 만든다. 두 주문은 저장되어 ID가 있어야 한다.
     *
     * @param execution 체결
     * @param executedAt 체결 일시
     * @return 체결 이벤트
     */
    public static TradeExecuted from(Execution execution, LocalDateTime executedAt) {
        return new TradeExecuted(execution.buyOrder().getLeagueId(),
                execution.buyOrder().getStockId(), execution.buyOrder().getId(),
                execution.sellOrder().getId(), execution.buyOrder().getPlayerId(),
                execution.sellOrder().getPlayerId(), execution.price(), execution.quantity(),
                executedAt, execution.restingOrder().getSide() == OrderSide.BUY);
    }

    /**
     * 대기 중이던 주문의 주인.
     *
     * @return 대기 주문이 매수면 매수자, 매도면 매도자 플레이어 ID
     */
    public long restingPlayerId() {
        return buyResting ? buyerPlayerId : sellerPlayerId;
    }
}
