package kr.ject.naspi.order.domain;

/**
 * 종목의 거래일별 체결 합계.
 *
 * @param quantity 체결 수량 합계 (주). 거래량
 * @param amount 체결 금액 합계 (원). 체결가 × 체결 수량의 합. 거래량가중평균가의 분자
 */
public record TradeTotal(long quantity, long amount) {
}
