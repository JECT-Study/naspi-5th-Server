package kr.ject.naspi.order.application;

/**
 * 체결 기록 1건의 체결가와 수량.
 *
 * @param price 체결가 (원)
 * @param quantity 체결 수량 (주)
 */
public record TradeRecord(long price, long quantity) {
}
