package kr.ject.naspi.websocket.api;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 실시간 메시지 설정 ({@code naspi.websocket}).
 *
 * @param heartbeat 서버와 클라이언트 간 heartbeat 전송 간격.
 * @param schedulerPoolSize 주기 발행 작업을 실행할 스레드 풀 크기.
 * @param topics 토픽별 주기 발행 설정.
 */
@ConfigurationProperties("naspi.websocket")
public record WebSocketProperties(
        @DefaultValue("10s") Duration heartbeat,
        @DefaultValue("2") int schedulerPoolSize,
        Map<String, Topic> topics) {

    /**
     * 설정값을 검증한다.
     *
     * @throws IllegalArgumentException heartbeat 간격이나 스레드 수가 0 이하일 때
     */
    public WebSocketProperties {
        if (heartbeat.isNegative() || heartbeat.isZero()) {
            throw new IllegalArgumentException("naspi.websocket.heartbeat는 0보다 커야 합니다");
        }
        if (schedulerPoolSize <= 0) {
            throw new IllegalArgumentException(
                    "naspi.websocket.scheduler-pool-size는 0보다 커야 합니다");
        }
        topics = topics == null ? Map.of() : Map.copyOf(topics);
    }

    /**
     * 주기가 설정된 토픽의 이름별 주기를 반환한다.
     *
     * @return 토픽 이름별 주기
     */
    public Map<String, Duration> intervals() {
        Map<String, Duration> intervals = new LinkedHashMap<>();
        topics.forEach((name, topic) -> {
            if (topic.interval() != null) {
                intervals.put(name, topic.interval());
            }
        });
        return intervals;
    }

    /**
     * 토픽별 설정.
     *
     * @param interval 주기 발행 간격 (예: {@code 10s}, {@code 1m}). 없으면 주기 발행하지 않는다
     */
    public record Topic(Duration interval) {

        /**
         * 설정값을 검증한다.
         *
         * @throws IllegalArgumentException 간격이 0 이하일 때
         */
        public Topic {
            if (interval != null && (interval.isNegative() || interval.isZero())) {
                throw new IllegalArgumentException("주기 발행 간격은 0보다 커야 합니다: " + interval);
            }
        }
    }
}
