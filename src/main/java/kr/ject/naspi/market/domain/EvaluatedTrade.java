package kr.ject.naspi.market.domain;

/**
 * 종료 평가 가격 계산에 반영하는 체결 1건.
 *
 * @param price 체결가 (원)
 * @param quantity 반영 수량 (주). 체결 수량 이하이다
 */
public record EvaluatedTrade(long price, long quantity) {
}
