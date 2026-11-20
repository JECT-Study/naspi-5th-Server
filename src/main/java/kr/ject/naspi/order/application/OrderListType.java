package kr.ject.naspi.order.application;

/**
 * 주문 목록의 종류.
 */
public enum OrderListType {

    /** 대기 주문. 미체결 잔여 수량이 있는 주문 (대기, 부분 체결). */
    PENDING,

    /** 완료된 주문. 수량의 일부 또는 전체가 체결·취소(만료 포함)된 주문. 전량 대기 중인 주문은 제외한다. */
    COMPLETED
}
