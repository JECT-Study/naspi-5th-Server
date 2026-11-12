package kr.ject.naspi.websocket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

    private static final Instant TIME = Instant.parse("2026-11-12T00:00:00.123Z");

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("WebSocket 메시지의 시각은 UTC epoch 밀리초 숫자로 내려준다")
    void writesTimesAsEpochMillis() {
        CandleChartResponse candles = new CandleChartResponse("종목", List.of(new Series(
                CandleInterval.MINUTE_10, List.of(new Candle(TIME, 1, 1, 1, 1, 0)))));

        String json = jsonMapper.writeValueAsString(new TopicEnvelope("T", candles, TIME));

        assertThat(json)
                .contains("\"publishedAt\":" + TIME.toEpochMilli())
                .contains("\"startedAt\":" + TIME.toEpochMilli());
    }
}
