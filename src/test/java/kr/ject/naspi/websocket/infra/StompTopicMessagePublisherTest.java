package kr.ject.naspi.websocket.infra;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.websocket.domain.TopicEnvelope;
import kr.ject.naspi.websocket.domain.TopicMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

class StompTopicMessagePublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

    private final SimpMessageSendingOperations messagingTemplate =
            mock(SimpMessageSendingOperations.class);
    private final StompTopicMessagePublisher publisher = new StompTopicMessagePublisher(
            messagingTemplate, Clock.fixed(NOW, ClockConfig.KST));

    @Test
    @DisplayName("메시지를 발행 대상 토픽으로 보내고, 본문에는 종류·데이터·발행 시각(KST)을 담는다")
    void publishesEnvelopeToDestination() {
        publisher.publish(new TopicMessage("/topic/leagues/1/ranking", "RANKING", Map.of("a", 1)));

        verify(messagingTemplate).convertAndSend("/topic/leagues/1/ranking",
                new TopicEnvelope("RANKING", Map.of("a", 1),
                        OffsetDateTime.ofInstant(NOW, ClockConfig.KST)));
    }

    @Test
    @DisplayName("발행에 실패해도 예외를 던지지 않아 호출한 본 작업에 영향을 주지 않는다")
    void swallowsPublishFailure() {
        doThrow(new MessageDeliveryException("브로커 오류"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> publisher.publish(new TopicMessage("/topic/a", "A", "data")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("발행 대상은 /topic/으로 시작해야 한다")
    void destinationMustBeTopic() {
        assertThatThrownBy(() -> new TopicMessage("/app/a", "A", "data"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
