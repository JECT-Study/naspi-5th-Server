package kr.ject.naspi.websocket.application.topic.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.Set;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse.Candle;
import kr.ject.naspi.websocket.api.response.chart.CandleChartResponse.Series;
import kr.ject.naspi.websocket.api.response.chart.CandleInterval;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandleChartTopicTest {

    private static final String CANDLES = "/topic/leagues/1/stocks/3/candles";

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final MutableClock clock = new MutableClock("2026-11-09T10:37:12+09:00");
    private final CandleChartTopic topic =
            new CandleChartTopic(subscriptions, clock, new Random(42));
    private final CandleChartTopicAuthorizer authorizer = new CandleChartTopicAuthorizer();

    @BeforeEach
    void setUp() {
        when(subscriptions.subscribedDestinations("/topic/leagues/")).thenReturn(Set.of(
                CANDLES, "/topic/leagues/1/stocks/3/orderbook", "/topic/leagues/1/stocks"));
    }

    @Test
    @DisplayName("구독 중인 캔들 차트 토픽에만 메시지를 만든다")
    void producesForSubscribedCandleTopics() {
        List<TopicMessage> messages = topic.produce();

        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.destination()).isEqualTo(CANDLES);
            assertThat(message.type()).isEqualTo("CANDLE_CHART");
        });
    }

    @Test
    @DisplayName("구독자가 없으면 캔들을 만들지 않는다")
    void producesNothingWithoutSubscribers() {
        when(subscriptions.subscribedDestinations("/topic/leagues/")).thenReturn(Set.of());

        assertThat(topic.produce()).isEmpty();
    }

    @Test
    @DisplayName("10분, 1시간, 1일 봉마다 현재 시각 기준 이전 봉과 현재 봉 2개만 보낸다 (1일봉은 09:00 거래일 기준)")
    void sendsTwoRecentCandlesBasedOnNow() {
        CandleChartResponse response = produceOnce();

        assertThat(response.series()).extracting(Series::interval).containsExactly(
                CandleInterval.MINUTE_10, CandleInterval.HOUR_1, CandleInterval.DAY_1);
        assertThat(times(response, CandleInterval.MINUTE_10))
                .containsExactly(kst("2026-11-09T10:20:00"), kst("2026-11-09T10:30:00"));
        assertThat(times(response, CandleInterval.HOUR_1))
                .containsExactly(kst("2026-11-09T09:00:00"), kst("2026-11-09T10:00:00"));
        assertThat(times(response, CandleInterval.DAY_1))
                .containsExactly(kst("2026-11-08T09:00:00"), kst("2026-11-09T09:00:00"));
    }

    @Test
    @DisplayName("09:00 이전은 전날 09:00에 시작한 거래일의 1일봉에 속한다")
    void dayCandleStartsAtNineKst() {
        assertThat(CandleChartTopic.startOf(CandleInterval.DAY_1,
                OffsetDateTime.parse("2026-11-09T08:59:59+09:00")))
                .isEqualTo(kst("2026-11-08T09:00:00"));
        assertThat(CandleChartTopic.startOf(CandleInterval.DAY_1,
                OffsetDateTime.parse("2026-11-09T09:00:00+09:00")))
                .isEqualTo(kst("2026-11-09T09:00:00"));
    }

    @Test
    @DisplayName("같은 구간에서는 이전 봉은 그대로이고, 현재 봉만 발행할 때마다 체결이 반영된다")
    void onlyCurrentCandleChanges() {
        CandleChartResponse first = produceOnce();
        clock.advance(Duration.ofSeconds(5));
        CandleChartResponse second = produceOnce();

        for (CandleInterval interval : CandleInterval.values()) {
            List<Candle> before = candles(first, interval);
            List<Candle> after = candles(second, interval);
            assertThat(after.get(0)).isEqualTo(before.get(0));
            assertThat(after.get(1).startedAt()).isEqualTo(before.get(1).startedAt());
            assertThat(after.get(1).openPrice()).isEqualTo(before.get(1).openPrice());
            assertThat(after.get(1).volume()).isGreaterThan(before.get(1).volume());
        }
    }

    @Test
    @DisplayName("봉 구간이 넘어가면 현재 봉이 마지막 값 그대로 이전 봉이 되고, 새 봉은 직전 종가로 열린다")
    void rollsOverToNewCandle() {
        Candle lastCurrent = candles(produceOnce(), CandleInterval.MINUTE_10).get(1);
        clock.advance(Duration.ofMinutes(10));

        List<Candle> candles = candles(produceOnce(), CandleInterval.MINUTE_10);

        assertThat(candles.get(0)).isEqualTo(lastCurrent);
        assertThat(candles.get(1).startedAt()).isEqualTo(kst("2026-11-09T10:40:00"));
        assertThat(candles.get(1).openPrice()).isEqualTo(lastCurrent.closePrice());
    }

    @Test
    @DisplayName("캔들 가격은 100원 호가 단위이고, 고가·저가가 시가·종가를 감싼다")
    void candlesAreOnTickUnit() {
        for (int i = 0; i < 50; i++) {
            clock.advance(Duration.ofMinutes(1));
            for (Series series : produceOnce().series()) {
                assertThat(series.candles()).allSatisfy(candle -> {
                    assertThat(List.of(candle.openPrice(), candle.highPrice(),
                            candle.lowPrice(), candle.closePrice()))
                            .allSatisfy(price -> assertThat(price % CandleChartTopic.TICK)
                                    .isZero());
                    assertThat(candle.highPrice()).isGreaterThanOrEqualTo(
                            Math.max(candle.openPrice(), candle.closePrice()));
                    assertThat(candle.lowPrice())
                            .isLessThanOrEqualTo(Math.min(candle.openPrice(), candle.closePrice()));
                });
            }
        }
    }

    @Test
    @DisplayName("캔들 차트는 로그인 회원 누구나 구독할 수 있다")
    void anyoneCanSubscribe() {
        assertThat(authorizer.supports(CANDLES)).isTrue();
        assertThat(authorizer.canSubscribe(new LoginMember(7L), CANDLES)).isTrue();
        assertThat(authorizer.supports("/topic/leagues/1/stocks/3/orderbook")).isFalse();
    }

    private CandleChartResponse produceOnce() {
        return (CandleChartResponse) topic.produce().getFirst().payload();
    }

    private static List<Candle> candles(CandleChartResponse response, CandleInterval interval) {
        return response.series().stream()
                .filter(series -> series.interval() == interval)
                .findFirst()
                .orElseThrow()
                .candles();
    }

    private static List<OffsetDateTime> times(CandleChartResponse response,
            CandleInterval interval) {
        return candles(response, interval).stream().map(Candle::startedAt).toList();
    }

    private static OffsetDateTime kst(String localDateTime) {
        return OffsetDateTime.parse(localDateTime + "+09:00");
    }

    /** 테스트에서 시각을 앞으로 옮길 수 있는 KST 시계. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(String offsetDateTime) {
            this.instant = OffsetDateTime.parse(offsetDateTime).toInstant();
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ClockConfig.KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
