package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderExecutionTotal;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderType;

/**
 * 주문 목록의 주문 1건 요약.
 *
 * @param orderId 주문 ID
 * @param stockId 종목 ID
 * @param stockName 종목 이름
 * @param side 매매 구분
 * @param type 주문 유형
 * @param orderedAt 접수 일시
 * @param quantity 최초 주문 수량
 * @param price 지정가 주문 가격. 시장가는 {@code null}
 * @param filledQuantity 체결 수량. 없으면 {@code null}
 * @param pendingQuantity 현재 대기 수량. 없으면 {@code null}
 * @param canceledQuantity 취소 수량 (거래일 변경 만료 수량 포함). 없으면 {@code null}
 * @param executionPrice 체결 단가. 체결 단가가 둘 이상이면 평균 체결 단가(원 미만 절사).
 *     체결 수량이 0이면 {@code null}
 * @param averagedPrice 체결 단가가 평균 체결 단가이면 {@code true}
 */
public record OrderSummary(long orderId, long stockId, String stockName, OrderSide side,
        OrderType type, LocalDateTime orderedAt, long quantity, Long price, Long filledQuantity,
        Long pendingQuantity, Long canceledQuantity, Long executionPrice, boolean averagedPrice) {

    /**
     * 주문과 체결 합계로 요약을 만든다. 체결·대기·취소 수량이 0이면 제공하지 않는다.
     *
     * @param order 주문
     * @param stockName 종목 이름
     * @param total 주문의 체결 합계
     * @return 주문 요약
     */
    static OrderSummary of(Order order, String stockName, OrderExecutionTotal total) {
        return new OrderSummary(order.getId(), order.getStockId(), stockName, order.getSide(),
                order.getType(), order.getOrderedAt(), order.getQuantity(), order.getPrice(),
                positiveOrNull(order.filledQuantity()), positiveOrNull(order.pendingQuantity()),
                positiveOrNull(order.canceledQuantity()), total.executionPrice(),
                total.quantity() > 0 && !total.hasSinglePrice());
    }

    private static Long positiveOrNull(long value) {
        return value > 0 ? value : null;
    }
}
