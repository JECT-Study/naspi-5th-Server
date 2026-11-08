package kr.ject.naspi.websocket.application;

import kr.ject.naspi.websocket.domain.TopicMessage;

/**
 * 토픽 메시지 발행 인터페이스. 이벤트 발행과 주기 발행이 모두 이 인터페이스로 발행한다.
 *
 * <p>현재 구현은 서버 한 대를 전제로 한 {@code infra.StompTopicMessagePublisher}다.
 * 서버를 여러 대로 늘리면 Redis Pub/Sub으로 모든 서버에 전달하는 구현으로 교체한다.
 */
public interface TopicMessagePublisher {

    /**
     * 토픽을 구독 중인 클라이언트에게 메시지를 보낸다. 발행에 실패해도 예외를 던지지 않는다.
     *
     * @param message 발행할 메시지
     */
    void publish(TopicMessage message);
}
