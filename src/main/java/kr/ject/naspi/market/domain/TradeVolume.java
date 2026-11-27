package kr.ject.naspi.market.domain;

import java.util.List;

/**
 * 체결 수량과 체결 금액 합계. 거래량가중평균가(VWAP)를 구한다.
 *
 * @param quantity 체결 수량 합계 (주)
 * @param amount 체결 금액 합계 (원). 체결가 × 수량의 합
 */
public record TradeVolume(long quantity, long amount) {

    /**
     * 체결들의 수량과 금액(체결가 × 반영 수량)을 합산한다.
     *
     * @param trades 반영할 체결
     * @return 합계
     */
    public static TradeVolume of(List<EvaluatedTrade> trades) {
        long quantity = 0;
        long amount = 0;
        for (EvaluatedTrade trade : trades) {
            quantity = Math.addExact(quantity, trade.quantity());
            amount = Math.addExact(amount, Math.multiplyExact(trade.price(), trade.quantity()));
        }
        return new TradeVolume(quantity, amount);
    }

    /**
     * 거래량가중평균가 {@code Σ(체결가 × 수량) / Σ수량}. 원 미만은 절사하고 100원 호가 단위로 맞추지 않는다
     * (league.md "리그 결과", 평가 전용 가격).
     *
     * @return 1주당 원
     * @throws IllegalStateException 수량 합계가 0이면
     */
    public long volumeWeightedAveragePrice() {
        if (quantity <= 0) {
            throw new IllegalStateException("체결 수량이 없어 평균가를 구할 수 없습니다.");
        }
        return amount / quantity;
    }
}
