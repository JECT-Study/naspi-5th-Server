package kr.ject.naspi.order.application;

/**
 * 종목의 거래일 체결 합계.
 *
 * @param quantity 체결 수량 합계 (주). 거래량
 * @param amount 체결 금액 합계 (원). 체결가 × 체결 수량의 합
 */
public record TradeDailyTotal(long quantity, long amount) {
}
