package kr.ject.naspi.asset.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * 참여자 본인의 자산 조회 결과. 금액은 원, 수량은 주 단위. 평가 가격은 현재가다.
 *
 * @param playerId 플레이어 ID
 * @param deposit 예수금. 예약금을 포함한 전체 현금
 * @param reservedDeposit 예약금. 미체결 매수 주문에 묶인 현금
 * @param orderableDeposit 주문 가능 현금 ({@code 예수금 - 예약금})
 * @param stockValuation 보유 주식 평가액 합계
 * @param totalAsset 총자산 ({@code 예수금 + 보유 주식 평가액})
 * @param totalProfit 총자산 평가 손익 ({@code 총자산 - 1억 원})
 * @param totalReturnRate 총자산 수익률(%) ({@code 총자산 평가 손익 ÷ 1억 원 × 100}).
 *     소수점 첫째 자리까지 반올림 (예: {@code 2.5}, {@code -12.4})
 * @param holdings 보유 종목. 종목 ID 순
 */
public record PlayerAsset(long playerId, long deposit, long reservedDeposit,
        long orderableDeposit, long stockValuation, long totalAsset, long totalProfit,
        BigDecimal totalReturnRate, List<HoldingAsset> holdings) {

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public PlayerAsset {
        holdings = List.copyOf(holdings);
    }

    /**
     * 보유 종목 하나.
     *
     * @param stockId 종목 ID
     * @param stockName 종목 이름
     * @param treasuryStock 내 종목(자사주) 여부
     * @param quantity 보유 수량. 예약 수량 포함
     * @param reservedQuantity 예약 수량. 미체결 매도 주문에 묶인 수량
     * @param orderableQuantity 매도 주문 가능 수량. 자사주는 최소 유지분 1,000주 제외
     * @param currentPrice 현재가
     * @param valuation 평가액 ({@code 보유 수량 × 현재가})
     * @param profit 평가 손익 ({@code 평가액 - 보유 수량 × 시초가 10,000원})
     * @param returnRate 수익률(%). 소수점 첫째 자리까지 반올림 (예: {@code 12.3}, {@code -12.4})
     */
    public record HoldingAsset(long stockId, String stockName, boolean treasuryStock,
            long quantity, long reservedQuantity, long orderableQuantity, long currentPrice,
            long valuation, long profit, BigDecimal returnRate) {
    }
}
