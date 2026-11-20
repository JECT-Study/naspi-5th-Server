package kr.ject.naspi.order.domain;

/**
 * 체결 기록에서 평가에 반영하는 부분.
 *
 * @param price 체결가 (원)
 * @param quantity 반영 수량 (주). 체결 수량 이하이다
 */
public record TradeSlice(long price, long quantity) {
}
