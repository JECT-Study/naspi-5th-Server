package kr.ject.naspi.websocket.api.response.stock;

import java.math.BigDecimal;
import java.util.List;

/**
 * 종목 정보 메시지. 리그의 전체 종목 시세.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/stocks}
 *
 * @param stock 종목 목록
 */
public record StockResponse(List<Stock> stock) {

    /** 메시지 종류 ({@code TopicEnvelope.type}). */
    public static final String TYPE = "STOCK";

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public StockResponse {
        stock = List.copyOf(stock);
    }

    /**
     * 종목 하나. 금액은 원 단위.
     *
     * @param stockId 종목 ID
     * @param stockName 종목명
     * @param currentPrice 현재가
     * @param changeAmount 등락액. 기준가 대비 변동 가격 (원)
     * @param changeRate 기준가 대비 등락률 (%). {@code (현재가 - 기준가) / 기준가 × 100}
     */
    public record Stock(long stockId, String stockName, long currentPrice, long changeAmount,
            BigDecimal changeRate) {
    }
}
