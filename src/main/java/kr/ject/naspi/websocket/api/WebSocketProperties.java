package kr.ject.naspi.websocket.api;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 실시간 메시지 설정 ({@code naspi.websocket}).
 *
 * @param heartbeat 서버·클라이언트 heartbeat 간격. 상대 신호가 일정 시간 없으면 연결을 끊는다
 */
@ConfigurationProperties("naspi.websocket")
public record WebSocketProperties(@DefaultValue("10s") Duration heartbeat) {

    /**
     * 설정값을 검증한다.
     *
     * @throws IllegalArgumentException heartbeat 간격이 0 이하일 때
     */
    public WebSocketProperties {
        if (heartbeat.isNegative() || heartbeat.isZero()) {
            throw new IllegalArgumentException("naspi.websocket.heartbeat는 0보다 커야 합니다");
        }
    }
}
