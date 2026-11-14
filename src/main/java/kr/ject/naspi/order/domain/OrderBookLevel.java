package kr.ject.naspi.order.domain;

/**
 * 호가창의 한 가격 단계.
 *
 * @param price 호가 (원)
 * @param quantity 이 가격에 대기 중인 잔여 수량 합계
 * @param orderCount 이 가격에 대기 중인 주문 수
 */
public record OrderBookLevel(long price, long quantity, int orderCount) {
}
