package kr.ject.naspi.league.domain;

/**
 * 리그 종료 시 참여자가 보유한 종목 하나의 평가 (LEA-13). 금액은 원, 수량은 주 단위.
 *
 * @param stockId 종목 ID
 * @param quantity 보유 수량
 * @param valuation 평가액 ({@code 보유 수량 × 종료 평가 가격})
 */
public record FinalHolding(long stockId, long quantity, long valuation) {
}
