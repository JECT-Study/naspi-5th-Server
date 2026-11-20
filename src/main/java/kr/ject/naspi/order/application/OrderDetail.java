package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderFill;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderType;

/**
 * 주문 1건의 상세 내역.
 *
 * @param orderId 주문 ID
 * @param stockId 종목 ID
 * @param stockName 종목 이름
 * @param side 매매 구분
 * @param type 주문 유형
 * @param orderedAt 접수 일시
 * @param quantity 최초 주문 수량
 * @param price 지정가 주문 가격. 시장가는 {@code null}
 * @param fills 체결 내역. 체결 일시 오름차순
 * @param cancellation 취소 내역. 취소 수량이 없으면 {@code null}
 * @param pendingQuantity 현재 대기 수량. 없으면 {@code null}
 */
public record OrderDetail(long orderId, long stockId, String stockName, OrderSide side,
        OrderType type, LocalDateTime orderedAt, long quantity, Long price, List<OrderFill> fills,
        Cancellation cancellation, Long pendingQuantity) {

    /**
     * 취소 내역.
     *
     * @param quantity 취소 수량 (거래일 변경 만료 수량 포함)
     * @param canceledAt 취소 일시
     * @param reason 취소 사유
     */
    public record Cancellation(long quantity, LocalDateTime canceledAt, CancelReason reason) {
    }

    /**
     * 주문과 체결 내역으로 상세 내역을 만든다. 취소·대기 수량이 0이면 제공하지 않는다.
     *
     * @param order 주문
     * @param stockName 종목 이름
     * @param fills 합산된 체결 내역
     * @return 주문 상세
     */
    static OrderDetail of(Order order, String stockName, List<OrderFill> fills) {
        long canceled = order.canceledQuantity();
        long pending = order.pendingQuantity();
        return new OrderDetail(order.getId(), order.getStockId(), stockName, order.getSide(),
                order.getType(), order.getOrderedAt(), order.getQuantity(), order.getPrice(),
                List.copyOf(fills),
                canceled > 0
                        ? new Cancellation(canceled, order.getCanceledAt(), order.getCancelReason())
                        : null,
                pending > 0 ? pending : null);
    }
}
