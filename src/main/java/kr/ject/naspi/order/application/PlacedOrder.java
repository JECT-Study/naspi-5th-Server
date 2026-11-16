package kr.ject.naspi.order.application;

import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderStatus;

/**
 * 접수된 주문의 접수 직후 결과.
 *
 * @param orderId 주문 ID
 * @param status 접수·매칭 직후 상태
 * @param quantity 주문 수량
 * @param remainingQuantity 미체결 잔여 수량. 자전거래로 취소된 경우 취소된 수량이다
 */
public record PlacedOrder(long orderId, OrderStatus status, long quantity,
        long remainingQuantity) {

    /**
     * 주문으로 결과를 만든다. 주문은 저장되어 ID가 있어야 한다.
     *
     * @param order 접수된 주문
     * @return 접수 결과
     */
    public static PlacedOrder from(Order order) {
        return new PlacedOrder(order.getId(), order.getStatus(), order.getQuantity(),
                order.getRemainingQuantity());
    }
}
