package kr.ject.naspi.websocket.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * 클라이언트가 받는 메시지 본문.
 *
 * <p>메시지는 변경분이 아니라 해당 시점의 전체 상태를 담는다. 클라이언트는 (재)연결 직후
 * REST로 현재 상태를 받고, 이후에는 {@code publishedAt}이 더 늦은 메시지로 덮어쓴다.
 *
 * @param type 메시지 종류
 * @param payload 데이터
 * @param publishedAt 발행 시각 (JSON: UTC epoch 밀리초)
 */
public record TopicEnvelope(String type, Object payload,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT) Instant publishedAt) {
}
