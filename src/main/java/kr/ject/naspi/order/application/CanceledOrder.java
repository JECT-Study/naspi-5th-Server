package kr.ject.naspi.order.application;

import kr.ject.naspi.order.domain.Order;

/**
 * 취소된 주문의 결과.
 *
 * @param orderId 주문 ID
 * @param quantity 주문 수량
 * @param filledQuantity 취소 전에 체결된 수량 (유지된다)
 * @param canceledQuantity 취소된 미체결 잔여 수량
 */
public record CanceledOrder(long orderId, long quantity, long filledQuantity,
        long canceledQuantity) {

    /**
     * 취소된 주문으로 결과를 만든다.
     *
     * @param order 취소된 주문
     * @return 취소 결과
     */
    public static CanceledOrder from(Order order) {
        return new CanceledOrder(order.getId(), order.getQuantity(), order.filledQuantity(),
                order.getRemainingQuantity());
    }
}
