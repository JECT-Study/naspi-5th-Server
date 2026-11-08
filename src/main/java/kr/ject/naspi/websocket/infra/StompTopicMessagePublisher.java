package kr.ject.naspi.websocket.infra;

import java.time.Clock;
import java.time.OffsetDateTime;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicEnvelope;
import kr.ject.naspi.websocket.domain.TopicMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

/**
 * 내장 STOMP 브로커로 메시지를 발행한다.
 *
 * <p>실시간 메시지는 부가 기능이므로 발행 실패가 체결 같은 본 작업을 실패시키지 않도록
 * 예외를 로그로 남기고 삼킨다. 클라이언트는 재연결 시 REST로 상태를 다시 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompTopicMessagePublisher implements TopicMessagePublisher {

    private final SimpMessageSendingOperations messagingTemplate;
    private final Clock clock;

    @Override
    public void publish(TopicMessage message) {
        TopicEnvelope envelope =
                new TopicEnvelope(message.type(), message.payload(), OffsetDateTime.now(clock));
        try {
            messagingTemplate.convertAndSend(message.destination(), envelope);
        } catch (MessagingException e) {
            log.warn("토픽 메시지 발행 실패: destination={}, type={}",
                    message.destination(), message.type(), e);
        }
    }
}
