package kr.ject.naspi.asset.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * 참여자 한 명의 총자산 집계 결과 (AST-08). 금액은 원, 수량은 주 단위.
 *
 * <p>평가 가격은 호출하는 쪽이 지정한 값이다. 평가 가격이 원 단위 정수라 원 미만이 생기지 않는다.
 *
 * @param playerId 플레이어 ID
 * @param deposit 예수금. 예약금을 포함한 전체 현금
 * @param stockValuation 보유 주식 평가액 합계 (자사주 포함)
 * @param totalAsset 총자산 ({@code 예수금 + 보유 주식 평가액})
 * @param totalProfit 총자산 손익 ({@code 총자산 − 1억 원})
 * @param totalReturnRate 총자산 수익률(%). 자산 조회(4.4)의 총자산 수익률과 같은 기준으로 소수점 둘째 자리에서
 *     반올림해 첫째 자리까지 (음수는 절댓값 기준)
 * @param withdrawn 탈퇴 여부. 탈퇴자는 현금이 0이고 보유 주식은 탈퇴 시점 수량 그대로다.
 *     결과에서 제외할지는 호출하는 쪽이 정한다
 * @param holdings 보유 수량이 있는 종목. 종목 ID 순
 */
public record PlayerTotalAsset(long playerId, long deposit, long stockValuation, long totalAsset,
        long totalProfit, BigDecimal totalReturnRate, boolean withdrawn,
        List<HoldingTotal> holdings) {

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public PlayerTotalAsset {
        holdings = List.copyOf(holdings);
    }

    /**
     * 보유 종목 하나의 평가.
     *
     * @param stockId 종목 ID
     * @param treasuryStock 자사주(본인이 상장한 종목) 여부
     * @param quantity 보유 수량. 예약 수량 포함
     * @param evaluationPrice 평가 가격 (1주당 원)
     * @param valuation 평가액 ({@code 보유 수량 × 평가 가격})
     */
    public record HoldingTotal(long stockId, boolean treasuryStock, long quantity,
            long evaluationPrice, long valuation) {
    }
}
