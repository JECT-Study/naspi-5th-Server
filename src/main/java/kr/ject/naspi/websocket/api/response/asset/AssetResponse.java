package kr.ject.naspi.websocket.api.response.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 자산 메시지. 금액은 원, 수량은 주 단위. 평가 가격은 현재가다.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/players/{playerId}/assets} (본인만 구독 가능)
 *
 * <p>REST {@code GET /api/leagues/{leagueId}/assets/me} 응답과 같은 필드에 {@code asOf}만 더한다.
 *
 * @param playerId 내 플레이어 ID
 * @param deposit 보유 현금 (예약금 포함)
 * @param orderableDeposit 주문 가능 현금 (보유 현금 - 예약금)
 * @param stockValuation 보유 주식 평가 금액 합계
 * @param totalAsset 총자산
 * @param totalProfit 총자산 평가 손익 (총자산 - 1억 원)
 * @param totalReturnRate 총자산 수익률(%). 소수점 첫째 자리까지
 * @param holdings 보유 종목 (보유 수량 1주 이상). 종목 ID 순
 * @param asOf 계산 시각 (KST). 표시 중인 값보다 이른 메시지는 무시한다
 */
public record AssetResponse(long playerId, long deposit, long orderableDeposit,
        long stockValuation, long totalAsset, long totalProfit, BigDecimal totalReturnRate,
        List<Holding> holdings, LocalDateTime asOf) {

    /** 메시지 종류 ({@code TopicEnvelope.type}). */
    public static final String TYPE = "ASSET";

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public AssetResponse {
        holdings = List.copyOf(holdings);
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
    public record Holding(long stockId, String stockName, boolean treasuryStock, long quantity,
            long orderableQuantity, long currentPrice, long averagePrice, long valuation,
            long profit, BigDecimal returnRate) {
    }
}
