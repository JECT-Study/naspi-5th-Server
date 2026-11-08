package kr.ject.naspi.websocket.api;

import java.security.Principal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP 연결·해제를 기록한다.
 *
 * <p>구독 현황은 Spring이 {@code SimpUserRegistry}에 자동으로 반영하므로 여기서 관리하지 않는다.
 */
@Slf4j
@Component
public class WebSocketConnectionListener {

    /**
     * 연결 완료를 기록한다.
     *
     * @param event 연결 이벤트
     */
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket 연결: sessionId={}, user={}",
                accessor.getSessionId(), nameOf(event.getUser()));
    }

    /**
     * 연결 해제를 기록한다.
     *
     * @param event 해제 이벤트
     */
    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        log.info("WebSocket 연결 해제: sessionId={}, user={}, status={}",
                event.getSessionId(), nameOf(event.getUser()), event.getCloseStatus());
    }

    private static String nameOf(Principal user) {
        return user == null ? null : user.getName();
    }
}
