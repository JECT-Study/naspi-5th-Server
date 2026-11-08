package kr.ject.naspi.websocket.api.response.orderbook;

import java.util.List;

/**
 * 호가 메시지. 종목 하나의 가격별 미체결 수량. 금액은 원, 수량은 주 단위.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/stocks/{stockId}/orderbook}
 *
 * @param currentPrice 현재가
 * @param asks 매도 호가. 낮은 가격(최우선 매도 호가)부터
 * @param bids 매수 호가. 높은 가격(최우선 매수 호가)부터
 */
public record OrderBookResponse(long currentPrice, List<Level> asks, List<Level> bids) {

    /** 메시지 종류 ({@code TopicEnvelope.type}). */
    public static final String TYPE = "ORDER_BOOK";

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public OrderBookResponse {
        asks = List.copyOf(asks);
        bids = List.copyOf(bids);
    }

    /**
     * 호가 한 단계.
     *
     * @param orderPrice 주문가격 (100원 단위)
     * @param remainingQuantity 이 가격 주문의 잔량 합계
     */
    public record Level(long orderPrice, long remainingQuantity) {
    }
}
