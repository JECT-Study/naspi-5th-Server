package kr.ject.naspi.websocket.domain;

import java.util.Objects;

/**
 * 토픽으로 발행할 메시지. 발행 대상(destination)과 데이터(type, payload)를 구분한다.
 *
 * @param destination 발행 대상 토픽. {@code /topic/}으로 시작한다 (예: {@code /topic/leagues/1/ranking})
 * @param type 클라이언트가 메시지 종류를 구분하는 값 (예: {@code RANKING})
 * @param payload 전달할 데이터. JSON으로 변환된다
 */
public record TopicMessage(String destination, String type, Object payload) {

    /** 브로커 토픽 접두사. */
    public static final String TOPIC_PREFIX = "/topic/";

    /**
     * 메시지를 만든다.
     *
     * @throws IllegalArgumentException 발행 대상이 {@code /topic/}으로 시작하지 않을 때
     */
    public TopicMessage {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(type, "type");
        if (!destination.startsWith(TOPIC_PREFIX)) {
            throw new IllegalArgumentException("토픽은 " + TOPIC_PREFIX + "으로 시작해야 합니다: "
                    + destination);
        }
    }
}
