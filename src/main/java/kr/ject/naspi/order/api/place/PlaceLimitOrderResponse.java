package kr.ject.naspi.order.api.place;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.ject.naspi.order.application.PlacedOrder;
import kr.ject.naspi.order.domain.OrderStatus;

/**
 * 지정가 주문 접수 결과.
 *
 * @param orderId 주문 ID
 * @param status 접수·매칭 직후 상태
 * @param quantity 주문 수량
 * @param remainingQuantity 미체결 잔여 수량 (자전거래로 취소된 경우 취소된 수량)
 */
public record PlaceLimitOrderResponse(
        @Schema(description = "주문 ID", example = "1") long orderId,
        @Schema(description = "접수·매칭 직후 상태 (PENDING 대기, PARTIALLY_FILLED 부분 체결, "
                + "FILLED 체결 완료, CANCELED 취소)", example = "PENDING") OrderStatus status,
        @Schema(description = "주문 수량 (주)", example = "10") long quantity,
        @Schema(description = "미체결 잔여 수량 (주). 자전거래로 취소된 경우 취소된 수량",
                example = "10") long remainingQuantity) {

    /**
     * 접수 결과로 응답을 만든다.
     *
     * @param placed 접수 결과
     * @return 응답
     */
    static PlaceLimitOrderResponse from(PlacedOrder placed) {
        return new PlaceLimitOrderResponse(placed.orderId(), placed.status(), placed.quantity(),
                placed.remainingQuantity());
    }
}
