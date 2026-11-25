package kr.ject.naspi.websocket.infra;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicEnvelope;
import kr.ject.naspi.websocket.domain.TopicMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

/**
 * 내장 STOMP 브로커로 메시지를 발행한다.
 *
 * <p>실시간 메시지는 부가 기능이므로 발행 실패가 체결 같은 본 작업을 실패시키지 않도록
 * 예외를 로그로 남기고 삼킨다. 클라이언트는 재연결 시 REST로 상태를 다시 맞춘다.
 *
 * <p>구독 클라이언트의 연결이 끊겨 전송하지 못하는 경우는 브로커가 세션별로 비동기 처리하므로 여기서
 * 예외가 되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompTopicMessagePublisher implements TopicMessagePublisher {

    private final SimpMessageSendingOperations messagingTemplate;
    private final Clock clock;

    @Override
    public void publish(TopicMessage message) {
        TopicEnvelope envelope = new TopicEnvelope(message.type(), message.payload(),
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
        try {
            messagingTemplate.convertAndSend(message.destination(), envelope);
        } catch (MessageConversionException e) {
            // payload를 JSON으로 바꾸지 못함. 응답 DTO 문제라 재시도해도 같다
            log.error("토픽 메시지 발행 실패 (JSON 변환 실패): destination={}, type={}",
                    message.destination(), message.type(), e);
        } catch (MessageDeliveryException e) {
            // 브로커 채널에 넘기지 못함 (서버 종료 중 등)
            log.warn("토픽 메시지 발행 실패 (브로커 전달 실패): destination={}, type={}",
                    message.destination(), message.type(), e);
        } catch (MessagingException e) {
            log.warn("토픽 메시지 발행 실패: destination={}, type={}",
                    message.destination(), message.type(), e);
        }
    }
}
