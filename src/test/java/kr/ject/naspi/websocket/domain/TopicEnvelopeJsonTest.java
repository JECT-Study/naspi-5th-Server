package kr.ject.naspi.websocket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse.Candle;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse.Series;
import kr.ject.naspi.websocket.api.response.chart.CandleInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
class TopicEnvelopeJsonTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 12, 9, 0, 0, 123_000_000);

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("WebSocket 메시지의 시각(발행 시각, 봉 시작 시각)은 REST와 같은 KST 일시 문자열로 내려준다")
    void writesTimesLikeRest() {
        CandleChartResponse candles = new CandleChartResponse("종목", List.of(new Series(
                CandleInterval.MINUTE_10, List.of(new Candle(TIME, 1, 1, 1, 1, 0)))));

        String json = jsonMapper.writeValueAsString(new TopicEnvelope("T", candles, TIME));

        assertThat(json)
                .contains("\"publishedAt\":\"2026-11-12T09:00:00.123\"")
                .contains("\"startedAt\":\"2026-11-12T09:00:00.123\"");
    }
}
