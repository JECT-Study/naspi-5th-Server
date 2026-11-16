package kr.ject.naspi.order.domain;

/**
 * 매칭으로 만들어진 체결 한 건.
 *
 * @param restingOrder 먼저 접수되어 호가창에 대기 중이던 주문
 * @param incomingOrder 새로 접수된 주문
 * @param price 체결 가격. 대기 중이던 주문의 가격이다
 * @param quantity 체결 수량
 */
public record Execution(Order restingOrder, Order incomingOrder, long price, long quantity) {

    /**
     * 매수 주문.
     *
     * @return 대기 주문과 신규 주문 중 매수 쪽
     */
    public Order buyOrder() {
        return restingOrder.getSide() == OrderSide.BUY ? restingOrder : incomingOrder;
    }

    /**
     * 매도 주문.
     *
     * @return 대기 주문과 신규 주문 중 매도 쪽
     */
    public Order sellOrder() {
        return restingOrder.getSide() == OrderSide.SELL ? restingOrder : incomingOrder;
    }

    /**
     * 체결 금액.
     *
     * @return {@code 체결 가격 × 체결 수량}
     */
    public long amount() {
        return Math.multiplyExact(price, quantity);
    }
}
