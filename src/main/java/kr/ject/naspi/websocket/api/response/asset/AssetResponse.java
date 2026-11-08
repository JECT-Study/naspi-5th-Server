package kr.ject.naspi.websocket.api.response.asset;

import java.util.List;

/**
 * 내 자산 메시지. 금액은 원, 수량은 주 단위.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/members/{memberId}/assets} (본인만 구독 가능)
 *
 * @param totalAsset 평가금 (예수금 + 보유 주식 평가액)
 * @param deposit 예수금. 예약금을 포함한 전체 현금
 * @param reservedDeposit 예약금. 예수금 중 미체결 매수 주문에 묶인 현금.
 *     주문 가능 현금은 {@code deposit - reservedDeposit}
 * @param stockValuation 보유 주식 평가액 합계
 * @param holdings 보유 종목
 */
public record AssetResponse(long totalAsset, long deposit, long reservedDeposit,
        long stockValuation, List<Holding> holdings) {

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
     * @param stockName 종목명
     * @param currentPrice 현재가
     * @param quantity 보유수량
     * @param valuation 평가액 (현재가 × 보유 수량)
     */
    public record Holding(long stockId, String stockName, long currentPrice, long quantity,
            long valuation) {
    }
}
