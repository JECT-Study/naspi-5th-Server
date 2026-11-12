package kr.ject.naspi.websocket.application.topic.chart;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse.Candle;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse.Series;
import kr.ject.naspi.websocket.api.response.chart.CandleInterval;
import kr.ject.naspi.websocket.application.PeriodicTopic;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 캔들 차트 토픽: {@code /topic/leagues/{leagueId}/stocks/{stockId}/candles}.
 *
 * <p>과거 캔들은 최초 접속 시 REST로 받고, 이 토픽은 봉 단위별로 최근 2개 캔들만 보낸다.
 * <ul>
 *   <li>이전 봉: 이미 끝난 구간이라 값이 바뀌지 않는다.</li>
 *   <li>현재 봉: 현재 시각이 속한 구간. 거래가 일어나면 종가·고가·저가·거래량이 바뀐다.</li>
 * </ul>
 * 봉 구간이 넘어가면 현재 봉이 마지막 값 그대로 이전 봉이 되고, 새 봉이 직전 종가로 열린다.
 *
 * <p>TODO: 실제 체결 데이터로 교체. 지금은 현재 봉 가격을 발행할 때마다 랜덤하게 움직이는 더미 응답을 보낸다.
 * 1일봉 구간 계산은 리그 모듈의 거래일 계산으로 교체한다.
 */
@Component
public class CandleChartTopic implements PeriodicTopic {

    /** 구독 경로. 리그 참여자 누구나 구독. */
    static final Pattern DESTINATION =
            Pattern.compile("^/topic/leagues/(\\d+)/stocks/(\\d+)/candles$");

    /** 호가 단위 (원). */
    static final long TICK = 100;

    private static final String PREFIX = "/topic/leagues/";
    private static final String DUMMY_STOCK_NAME = "진영전자";
    private static final long DUMMY_FIRST_PRICE = 10_000;
    /** 거래일 시작 시각 (09:00 KST). */
    private static final Duration TRADING_DAY_START = Duration.ofHours(9);

    private final TopicSubscriptions subscriptions;
    private final Clock clock;
    private final RandomGenerator random;
    private final Map<CandleInterval, RecentCandles> recent = new EnumMap<>(CandleInterval.class);

    /**
     * 토픽을 만든다.
     *
     * @param subscriptions 구독 현황
     * @param clock 현재 시각
     */
    @Autowired
    public CandleChartTopic(TopicSubscriptions subscriptions, Clock clock) {
        this(subscriptions, clock, new Random());
    }

    CandleChartTopic(TopicSubscriptions subscriptions, Clock clock, RandomGenerator random) {
        this.subscriptions = subscriptions;
        this.clock = clock;
        this.random = random;
    }

    @Override
    public String name() {
        return "candles";
    }

    @Override
    public String destinationPrefix() {
        return PREFIX;
    }

    @Override
    public List<TopicMessage> produce() {
        List<String> destinations = subscriptions.subscribedDestinations(PREFIX).stream()
                .filter(destination -> DESTINATION.matcher(destination).matches())
                .sorted()
                .toList();
        if (destinations.isEmpty()) {
            return List.of();
        }
        CandleChartResponse response = nextDummy();
        return destinations.stream()
                .map(destination -> new TopicMessage(destination, CandleChartResponse.TYPE,
                        response))
                .toList();
    }

    private synchronized CandleChartResponse nextDummy() {
        Instant now = clock.instant();
        List<Series> series = Arrays.stream(CandleInterval.values())
                .map(interval -> new Series(interval, advance(interval, now)))
                .toList();
        return new CandleChartResponse(DUMMY_STOCK_NAME, series);
    }

    /** 봉 단위 하나의 최근 2개 캔들을 현재 시각 기준으로 갱신한다. */
    private List<Candle> advance(CandleInterval interval, Instant now) {
        Instant start = startOf(interval, now);
        Instant previousStart = start.minus(interval.duration());
        RecentCandles candles = recent.get(interval);
        if (candles == null) {
            Candle previous = new Candle(previousStart, DUMMY_FIRST_PRICE,
                    DUMMY_FIRST_PRICE + 2 * TICK, DUMMY_FIRST_PRICE - TICK,
                    DUMMY_FIRST_PRICE + TICK, 100);
            candles = new RecentCandles(previous, open(start, previous.closePrice()));
        } else if (candles.current().startedAt().isBefore(start)) {
            // 봉 구간이 넘어감: 진행 중이던 봉은 확정되어 이전 봉이 된다.
            Candle previous = candles.current();
            if (!previous.startedAt().equals(previousStart)) {
                // 여러 구간을 건너뛰었으면 직전 구간은 거래가 없던 봉이다.
                previous = open(previousStart, previous.closePrice());
            }
            candles = new RecentCandles(previous, open(start, previous.closePrice()));
        }
        candles = new RecentCandles(candles.previous(), trade(candles.current()));
        recent.put(interval, candles);
        return List.of(candles.previous(), candles.current());
    }

    /** 현재 봉에 랜덤 체결 하나를 반영한다. 종가는 ±2호가 안에서 움직인다. */
    private Candle trade(Candle current) {
        long close = Math.max(TICK, current.closePrice() + (random.nextInt(5) - 2) * TICK);
        return new Candle(current.startedAt(), current.openPrice(),
                Math.max(current.highPrice(), close), Math.min(current.lowPrice(), close), close,
                current.volume() + random.nextInt(1, 21));
    }

    private static Candle open(Instant start, long price) {
        return new Candle(start, price, price, price, price, 0);
    }

    /** 현재 시각이 속한 봉의 시작 시각. 봉 경계는 KST 기준이고 1일봉은 거래일(09:00 KST 시작) 기준. */
    static Instant startOf(CandleInterval interval, Instant now) {
        ZonedDateTime kst = now.atZone(ClockConfig.KST);
        ZonedDateTime start = switch (interval) {
            case MINUTE_10 -> kst.truncatedTo(ChronoUnit.HOURS)
                    .plusMinutes(kst.getMinute() / 10 * 10L);
            case HOUR_1 -> kst.truncatedTo(ChronoUnit.HOURS);
            case DAY_1 -> kst.minus(TRADING_DAY_START).truncatedTo(ChronoUnit.DAYS)
                    .plus(TRADING_DAY_START);
        };
        return start.toInstant();
    }

    /**
     * 봉 단위 하나의 최근 2개 캔들.
     *
     * @param previous 이전 봉 (확정)
     * @param current 현재 봉 (진행 중)
     */
    private record RecentCandles(Candle previous, Candle current) {
    }
}
