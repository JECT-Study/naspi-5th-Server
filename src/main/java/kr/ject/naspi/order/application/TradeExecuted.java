package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import kr.ject.naspi.order.domain.Execution;

/**
 * 체결 이벤트. 체결 1건이 정산된 뒤 발행한다 (docs/policies/README.md 이벤트 표).
 *
 * <p>market은 현재가와 거래량을 갱신하고(MKT-05), websocket은 커밋 이후 매수자·매도자의 자산을
 * 발행한다. 체결 1건에 이벤트 1건이므로 거래량은 이 이벤트마다 한 번씩 합산한다.
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
 */
public record TradeExecuted(long leagueId, long stockId, long buyOrderId, long sellOrderId,
        long buyerPlayerId, long sellerPlayerId, long price, long quantity,
        LocalDateTime executedAt) {

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
                executedAt);
    }
}
