package kr.ject.naspi.order.api.orderbook;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import kr.ject.naspi.order.application.OrderBookView;

/**
 * 호가 조회 응답. 금액은 원, 수량은 주 단위.
 *
 * @param currentPrice 종목 현재가
 * @param asks 매도 호가. 낮은 가격부터 최대 5단계. 없으면 빈 배열
 * @param bids 매수 호가. 높은 가격부터 최대 5단계. 없으면 빈 배열
 */
record OrderBookResponse(
        @Schema(description = "종목 현재가(원)", example = "10000") long currentPrice,
        @Schema(description = "매도 호가. 낮은 가격(최우선 매도 호가)부터 최대 5단계. 없으면 빈 배열")
        List<Level> asks,
        @Schema(description = "매수 호가. 높은 가격(최우선 매수 호가)부터 최대 5단계. 없으면 빈 배열")
        List<Level> bids) {

    static OrderBookResponse from(OrderBookView view) {
        return new OrderBookResponse(view.currentPrice(), toLevels(view.asks()),
                toLevels(view.bids()));
    }

    private static List<Level> toLevels(List<OrderBookView.PriceLevel> levels) {
        return levels.stream()
                .map(level -> new Level(level.price(), level.quantity()))
                .toList();
    }

    /**
     * 호가 한 단계.
     *
     * @param orderPrice 주문 가격
     * @param remainingQuantity 이 가격의 미체결 잔여 수량 합계
     */
    record Level(
            @Schema(description = "주문 가격(원, 100원 단위)", example = "10100") long orderPrice,
            @Schema(description = "이 가격의 미체결 잔여 수량 합계(주)", example = "7")
            long remainingQuantity) {
    }
}
