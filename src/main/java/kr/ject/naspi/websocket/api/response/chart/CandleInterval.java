package kr.ject.naspi.websocket.api.response.chart;

import java.time.Duration;

/** 캔들 차트 봉 단위. */
public enum CandleInterval {

    /** 10분봉. */
    MINUTE_10(Duration.ofMinutes(10)),
    /** 1시간봉. */
    HOUR_1(Duration.ofHours(1)),
    /** 1일봉. */
    DAY_1(Duration.ofDays(1));

    private final Duration duration;

    CandleInterval(Duration duration) {
        this.duration = duration;
    }

    /**
     * 봉 하나가 차지하는 시간.
     *
     * @return 봉 길이
     */
    public Duration duration() {
        return duration;
    }
}
