package kr.ject.naspi.websocket.api.response.chart;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 캔들 차트 메시지. 종목 하나의 봉 단위별(10분, 1시간, 1일) 캔들 목록.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/stocks/{stockId}/candles}
 *
 * @param stockName 종목명
 * @param series 봉 단위별 캔들 목록
 */
public record CandleChartResponse(String stockName, List<Series> series) {

    /** 메시지 종류 ({@code TopicEnvelope.type}). */
    public static final String TYPE = "CANDLE_CHART";

    /**
     * 목록을 복사해 변경할 수 없게 만든다.
     */
    public CandleChartResponse {
        series = List.copyOf(series);
    }

    /**
     * 한 봉 단위의 캔들 목록.
     *
     * @param interval 봉 단위
     * @param candles 시간 오름차순 캔들
     */
    public record Series(CandleInterval interval, List<Candle> candles) {

        /**
         * 목록을 복사해 변경할 수 없게 만든다.
         */
        public Series {
            candles = List.copyOf(candles);
        }
    }

    /**
     * 캔들 하나. 금액은 원, 거래량은 주 단위.
     *
     * @param startedAt 봉 시작 시각 (KST)
     * @param openPrice 시가
     * @param highPrice 고가
     * @param lowPrice 저가
     * @param closePrice 종가
     * @param volume 거래량
     */
    public record Candle(OffsetDateTime startedAt, long openPrice, long highPrice,
            long lowPrice, long closePrice, long volume) {
    }
}
