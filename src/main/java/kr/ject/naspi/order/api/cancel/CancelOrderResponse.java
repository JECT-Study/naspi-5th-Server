package kr.ject.naspi.order.api.cancel;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.ject.naspi.order.application.CanceledOrder;

/**
 * 주문 취소 결과.
 *
 * @param orderId 주문 ID
 * @param quantity 주문 수량
 * @param filledQuantity 취소 전에 체결된 수량
 * @param canceledQuantity 취소된 미체결 잔여 수량
 */
public record CancelOrderResponse(
        @Schema(description = "주문 ID", example = "1") long orderId,
        @Schema(description = "주문 수량 (주)", example = "10") long quantity,
        @Schema(description = "취소 전에 체결된 수량 (주). 취소해도 유지된다", example = "3")
        long filledQuantity,
        @Schema(description = "취소된 미체결 잔여 수량 (주)", example = "7") long canceledQuantity) {

    /**
     * 취소 결과로 응답을 만든다.
     *
     * @param canceled 취소 결과
     * @return 응답
     */
    static CancelOrderResponse from(CanceledOrder canceled) {
        return new CancelOrderResponse(canceled.orderId(), canceled.quantity(),
                canceled.filledQuantity(), canceled.canceledQuantity());
    }
}
