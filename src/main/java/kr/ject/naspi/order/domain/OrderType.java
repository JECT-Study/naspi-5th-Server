package kr.ject.naspi.order.domain;

/**
 * 주문 유형.
 */
public enum OrderType {

    /** 지정가 주문. 가격과 수량을 지정하며 미체결 잔여 수량은 대기 주문으로 유지된다. */
    LIMIT,

    /** 시장가 주문. 수량만 지정하며 보호 조건 안에서 즉시 체결하고 잔여 수량은 취소된다. */
    MARKET
}
