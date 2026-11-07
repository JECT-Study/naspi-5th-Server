package kr.ject.naspi.websocket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/**
 * 토픽별 주기 발행 예약을 검증한다. 실제 시간을 기다리지 않도록 예약 요청을 가로채서 확인한다.
 */
class TopicSchedulerTest {

    private RecordingTaskScheduler taskScheduler;
    private TopicSubscriptions subscriptions;
    private TopicMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        taskScheduler = new RecordingTaskScheduler();
        subscriptions = mock(TopicSubscriptions.class);
        publisher = mock(TopicMessagePublisher.class);
        when(subscriptions.hasSubscribers(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("주기 설정이 있는 토픽만 예약하고, 설정이 없는 토픽은 스케줄링 대상에서 제외한다")
    void schedulesOnlyConfiguredTopics() {
        TopicScheduler scheduler = scheduler(
                List.of(new FakeTopic("ranking"), new FakeTopic("news")),
                Map.of("ranking", Duration.ofSeconds(10)));

        scheduler.start();

        assertThat(taskScheduler.intervals).containsOnlyKeys("ranking");
    }

    @Test
    @DisplayName("토픽마다 설정된 주기가 독립적으로 적용된다")
    void appliesIntervalPerTopic() {
        TopicScheduler scheduler = scheduler(
                List.of(new FakeTopic("stock"), new FakeTopic("market"), new FakeTopic("ranking")),
                Map.of("stock", Duration.ofSeconds(1),
                        "market", Duration.ofMinutes(1),
                        "ranking", Duration.ofSeconds(10)));

        scheduler.start();

        assertThat(taskScheduler.intervals).containsExactlyInAnyOrderEntriesOf(Map.of(
                "stock", Duration.ofSeconds(1),
                "market", Duration.ofMinutes(1),
                "ranking", Duration.ofSeconds(10)));
    }

    @Test
    @DisplayName("새로운 토픽을 추가해도 기존 토픽의 예약 주기는 바뀌지 않는다")
    void addingTopicDoesNotAffectExistingTopics() {
        Map<String, Duration> before = scheduledIntervals(
                List.of(new FakeTopic("ranking")), Map.of("ranking", Duration.ofSeconds(10)));

        Map<String, Duration> after = scheduledIntervals(
                List.of(new FakeTopic("ranking"), new FakeTopic("market")),
                Map.of("ranking", Duration.ofSeconds(10), "market", Duration.ofMinutes(1)));

        assertThat(after).containsAllEntriesOf(before);
    }

    @Test
    @DisplayName("예약된 토픽이 실행되면 해당 토픽의 데이터만 만들어 발행한다")
    void runPublishesOnlyOwnTopicMessages() {
        FakeTopic ranking = new FakeTopic("ranking");
        FakeTopic market = new FakeTopic("market");
        TopicScheduler scheduler = scheduler(List.of(ranking, market),
                Map.of("ranking", Duration.ofSeconds(10), "market", Duration.ofMinutes(1)));
        scheduler.start();

        taskScheduler.run("ranking");

        assertThat(ranking.produced.get()).isEqualTo(1);
        assertThat(market.produced.get()).isZero();
        verify(publisher).publish(ranking.message());
    }

    @Test
    @DisplayName("구독자가 없는 토픽은 데이터를 조회하지 않고 발행도 하지 않는다")
    void skipsWhenNoSubscribers() {
        FakeTopic ranking = new FakeTopic("ranking");
        when(subscriptions.hasSubscribers(ranking.destinationPrefix())).thenReturn(false);
        TopicScheduler scheduler =
                scheduler(List.of(ranking), Map.of("ranking", Duration.ofSeconds(10)));
        scheduler.start();

        taskScheduler.run("ranking");

        assertThat(ranking.produced.get()).isZero();
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("한 토픽의 데이터 생성이 실패해도 예외가 퍼지지 않아 다음 주기와 다른 토픽은 계속 실행된다")
    void failureIsIsolated() {
        FakeTopic failing = new FakeTopic("failing") {
            @Override
            public List<TopicMessage> produce() {
                produced.incrementAndGet();
                throw new IllegalStateException("조회 실패");
            }
        };
        FakeTopic ranking = new FakeTopic("ranking");
        TopicScheduler scheduler = scheduler(List.of(failing, ranking),
                Map.of("failing", Duration.ofSeconds(1), "ranking", Duration.ofSeconds(10)));
        scheduler.start();

        taskScheduler.run("failing");
        taskScheduler.run("failing");
        taskScheduler.run("ranking");

        assertThat(failing.produced.get()).isEqualTo(2);
        verify(publisher, times(1)).publish(eq(ranking.message()));
    }

    @Test
    @DisplayName("종료하면 예약된 주기 발행을 모두 취소한다")
    void stopCancelsSchedules() {
        TopicScheduler scheduler = scheduler(List.of(new FakeTopic("ranking")),
                Map.of("ranking", Duration.ofSeconds(10)));
        scheduler.start();

        scheduler.stop();

        assertThat(scheduler.isRunning()).isFalse();
        taskScheduler.futures.forEach(future -> verify(future).cancel(false));
    }

    @Test
    @DisplayName("주기 발행 토픽 이름이 중복되면 시작하지 않는다")
    void rejectsDuplicateNames() {
        assertThatThrownBy(() -> scheduler(
                List.of(new FakeTopic("ranking"), new FakeTopic("ranking")), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ranking");
    }

    private TopicScheduler scheduler(List<PeriodicTopic> topics, Map<String, Duration> intervals) {
        return new TopicScheduler(taskScheduler, topics, intervals, subscriptions, publisher);
    }

    private Map<String, Duration> scheduledIntervals(List<PeriodicTopic> topics,
            Map<String, Duration> intervals) {
        taskScheduler = new RecordingTaskScheduler();
        scheduler(topics, intervals).start();
        return taskScheduler.intervals;
    }

    /** 이름별로 하나의 메시지를 만드는 테스트 토픽. */
    private static class FakeTopic implements PeriodicTopic {

        final AtomicInteger produced = new AtomicInteger();
        private final String name;

        FakeTopic(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String destinationPrefix() {
            return "/topic/" + name;
        }

        @Override
        public List<TopicMessage> produce() {
            produced.incrementAndGet();
            return List.of(message());
        }

        TopicMessage message() {
            return new TopicMessage(destinationPrefix(), name.toUpperCase(), name);
        }
    }

    /** 예약 요청을 토픽 이름별로 기록하고, 원하는 시점에 직접 실행하는 스케줄러. */
    private static class RecordingTaskScheduler implements TaskScheduler {

        final Map<String, Duration> intervals = new LinkedHashMap<>();
        final List<ScheduledFuture<?>> futures = new ArrayList<>();
        private final Map<String, Runnable> tasks = new LinkedHashMap<>();

        void run(String name) {
            tasks.get(name).run();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            String name = ((TopicScheduler.TopicTask) task).topicName();
            tasks.put(name, task);
            intervals.put(name, delay);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            futures.add(future);
            return future;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task,
                Instant startTime, Duration delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime,
                Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            throw new UnsupportedOperationException();
        }
    }
}
