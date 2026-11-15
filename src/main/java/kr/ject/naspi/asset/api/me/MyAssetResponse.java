package kr.ject.naspi.asset.api.me;

import java.math.BigDecimal;
import java.util.List;
import kr.ject.naspi.asset.application.PlayerAsset;
import kr.ject.naspi.asset.application.PlayerAsset.HoldingAsset;

/**
 * 내 자산 조회 응답. 금액은 원, 수량은 주 단위. 평가 가격은 현재가다.
 *
 * @param playerId 내 플레이어 ID. 웹소켓 내 자산 토픽 구독 경로에 쓴다
 * @param deposit 보유 현금 (예약금 포함)
 * @param orderableDeposit 주문 가능 현금 (보유 현금 - 예약금)
 * @param stockValuation 보유 주식 평가 금액 합계
 * @param totalAsset 총자산
 * @param totalProfit 총자산 평가 손익 (총자산 - 1억 원)
 * @param totalReturnRate 총자산 수익률(%). 소수점 첫째 자리까지
 * @param holdings 보유 종목 (보유 수량 1주 이상). 종목 ID 순
 */
record MyAssetResponse(long playerId, long deposit, long orderableDeposit, long stockValuation,
        long totalAsset, long totalProfit, BigDecimal totalReturnRate, List<Holding> holdings) {

    static MyAssetResponse from(PlayerAsset asset) {
        return new MyAssetResponse(asset.playerId(), asset.deposit(), asset.orderableDeposit(),
                asset.stockValuation(), asset.totalAsset(), asset.totalProfit(),
                asset.totalReturnRate(), asset.holdings().stream().map(Holding::from).toList());
    }

    /**
     * 보유 종목 하나.
     *
     * @param stockId 종목 ID
     * @param stockName 종목 이름
     * @param treasuryStock 자사주(내 종목) 여부
     * @param quantity 보유 수량 (예약 수량 포함)
     * @param orderableQuantity 매도 주문 가능 수량
     * @param currentPrice 현재가
     * @param averagePrice 평균 매입가 (이동평균법, 매입 금액 ÷ 보유 수량). 원 미만 절사
     * @param valuation 평가 금액 (보유 수량 × 현재가)
     * @param profit 평가 손익 (평가 금액 - 매입 금액)
     * @param returnRate 수익률(%). 소수점 첫째 자리까지
     */
    record Holding(long stockId, String stockName, boolean treasuryStock, long quantity,
            long orderableQuantity, long currentPrice, long averagePrice,
            long valuation, long profit, BigDecimal returnRate) {

        static Holding from(HoldingAsset holding) {
            return new Holding(holding.stockId(), holding.stockName(), holding.treasuryStock(),
                    holding.quantity(), holding.orderableQuantity(), holding.currentPrice(),
                    holding.averagePrice(), holding.valuation(), holding.profit(),
                    holding.returnRate());
        }
    }
}
