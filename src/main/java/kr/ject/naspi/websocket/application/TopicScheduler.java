package kr.ject.naspi.websocket.application;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

/**
 * {@link PeriodicTopic}을 토픽마다 독립적으로 예약해 실행한다.
 *
 * <ul>
 *   <li>주기 설정이 없는 토픽은 예약하지 않는다.</li>
 *   <li>토픽마다 별도로 {@code fixedDelay} 예약하므로 같은 토픽은 겹쳐 실행되지 않고,
 *       한 토픽이 느려지거나 실패해도 다른 토픽의 주기에 영향이 없다.
 *       (스레드 풀 크기가 동시에 실행될 토픽 수보다 작으면 서로 기다릴 수 있다)</li>
 *   <li>구독자가 없으면 데이터를 조회하지 않는다.</li>
 *   <li>서버가 여러 대여도 각 서버는 자기 접속자에게만 보내므로 중복 발행이 되지 않는다.</li>
 * </ul>
 */
@Slf4j
public class TopicScheduler implements SmartLifecycle {

    private final TaskScheduler taskScheduler;
    private final List<PeriodicTopic> topics;
    private final Map<String, Duration> intervals;
    private final TopicSubscriptions subscriptions;
    private final TopicMessagePublisher publisher;
    private final Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();
    private volatile boolean running;

    /**
     * 스케줄러를 만든다.
     *
     * @param taskScheduler 주기 실행에 쓸 스케줄러
     * @param topics 주기 발행 토픽 빈
     * @param intervals 토픽 이름별 발행 주기. 없는 토픽은 예약하지 않는다
     * @param subscriptions 구독 현황
     * @param publisher 메시지 발행기
     * @throws IllegalStateException 토픽 이름이 중복될 때
     */
    public TopicScheduler(TaskScheduler taskScheduler, List<PeriodicTopic> topics,
            Map<String, Duration> intervals, TopicSubscriptions subscriptions,
            TopicMessagePublisher publisher) {
        this.taskScheduler = taskScheduler;
        this.topics = List.copyOf(topics);
        this.intervals = Map.copyOf(intervals);
        this.subscriptions = subscriptions;
        this.publisher = publisher;
        validateUniqueNames(this.topics);
    }

    @Override
    public void start() {
        warnUnknownIntervals();
        for (PeriodicTopic topic : topics) {
            Duration interval = intervals.get(topic.name());
            if (interval == null) {
                log.info("주기 발행 설정이 없어 예약하지 않음: topic={}", topic.name());
                continue;
            }
            scheduled.put(topic.name(),
                    taskScheduler.scheduleWithFixedDelay(new TopicTask(topic), interval));
            log.info("주기 발행 예약: topic={}, interval={}", topic.name(), interval);
        }
        running = true;
    }

    @Override
    public void stop() {
        scheduled.values().forEach(future -> future.cancel(false));
        scheduled.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 토픽을 한 번 실행한다. 구독자가 없으면 건너뛰고, 실패해도 다음 주기는 계속 실행된다.
     *
     * @param topic 실행할 토픽
     */
    void runOnce(PeriodicTopic topic) {
        try {
            if (!subscriptions.hasSubscribers(topic.destinationPrefix())) {
                return;
            }
            topic.produce().forEach(publisher::publish);
        } catch (RuntimeException e) {
            log.error("주기 발행 실패: topic={}", topic.name(), e);
        }
    }

    /** 토픽 하나의 주기 실행 작업. 어떤 토픽의 작업인지 알 수 있도록 토픽을 함께 둔다. */
    final class TopicTask implements Runnable {

        private final PeriodicTopic topic;

        TopicTask(PeriodicTopic topic) {
            this.topic = topic;
        }

        String topicName() {
            return topic.name();
        }

        @Override
        public void run() {
            runOnce(topic);
        }
    }

    private void warnUnknownIntervals() {
        Set<String> names = topics.stream().map(PeriodicTopic::name).collect(Collectors.toSet());
        intervals.keySet().stream()
                .filter(name -> !names.contains(name))
                .forEach(name -> log.warn("주기 설정에 해당하는 토픽이 없음: topic={}", name));
    }

    private static void validateUniqueNames(List<PeriodicTopic> topics) {
        Set<String> names = new HashSet<>();
        for (PeriodicTopic topic : topics) {
            if (!names.add(topic.name())) {
                throw new IllegalStateException("주기 발행 토픽 이름 중복: " + topic.name());
            }
        }
    }
}
