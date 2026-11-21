package kr.ject.naspi.order.api.place;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.ject.naspi.order.application.PlacedOrder;
import kr.ject.naspi.order.domain.OrderStatus;

/**
 * 시장가 주문 접수 결과.
 *
 * @param orderId 주문 ID
 * @param status 접수·매칭 직후 상태 ({@code FILLED} 또는 {@code CANCELED})
 * @param quantity 주문 수량
 * @param remainingQuantity 미체결 잔여 수량 (취소된 경우 취소된 수량)
 */
public record PlaceMarketOrderResponse(
        @Schema(description = "주문 ID", example = "1") long orderId,
        @Schema(description = "접수·매칭 직후 상태 (FILLED 체결 완료, CANCELED 잔량 취소)",
                example = "FILLED") OrderStatus status,
        @Schema(description = "주문 수량 (주)", example = "10") long quantity,
        @Schema(description = "미체결 잔여 수량 (주). 취소된 경우 취소된 수량",
                example = "0") long remainingQuantity) {

    /**
     * 접수 결과로 응답을 만든다.
     *
     * @param placed 접수 결과
     * @return 응답
     */
    static PlaceMarketOrderResponse from(PlacedOrder placed) {
        return new PlaceMarketOrderResponse(placed.orderId(), placed.status(), placed.quantity(),
                placed.remainingQuantity());
    }
}
