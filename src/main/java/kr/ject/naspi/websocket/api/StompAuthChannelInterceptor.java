package kr.ject.naspi.websocket.api;

import java.security.Principal;
import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import kr.ject.naspi.websocket.domain.TopicMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;

/**
 * 클라이언트가 보낸 STOMP 프레임의 인증·인가를 확인한다.
 *
 * <p>로그인 확인은 WebSocket 핸드셰이크(HTTP)에서 Spring Security가 먼저 한다. 세션에 로그인 정보가
 * 없으면 핸드셰이크가 401로 거부된다. 이 인터셉터는 STOMP 단계에서 한 번 더 확인한다.
 *
 * <ul>
 *   <li>CONNECT: 로그인 회원만 허용</li>
 *   <li>SUBSCRIBE: {@code /topic/} 토픽 중 담당 {@link TopicSubscriptionAuthorizer}가 허용한 것만</li>
 *   <li>SEND: {@code /app/}만 허용. 클라이언트가 브로커 토픽으로 직접 보내 다른 사용자에게
 *       퍼뜨리는 것을 막는다</li>
 * </ul>
 */
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** 클라이언트가 서버로 보낼 수 있는 목적지 접두사. */
    static final String APPLICATION_PREFIX = "/app/";

    private final List<TopicSubscriptionAuthorizer> authorizers;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            loginMember(accessor.getUser());
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(loginMember(accessor.getUser()), accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            authorizeSend(accessor.getDestination());
        }
        return message;
    }

    private void authorizeSubscribe(LoginMember member, String destination) {
        if (destination == null || !destination.startsWith(TopicMessage.TOPIC_PREFIX)) {
            throw new MessageDeliveryException("구독할 수 없는 토픽입니다: " + destination);
        }
        boolean allowed = authorizers.stream()
                .filter(authorizer -> authorizer.supports(destination))
                .findFirst()
                .map(authorizer -> authorizer.canSubscribe(member, destination))
                .orElse(false);
        if (!allowed) {
            throw new MessageDeliveryException("토픽 구독 권한이 없습니다: " + destination);
        }
    }

    private static void authorizeSend(String destination) {
        if (destination == null || !destination.startsWith(APPLICATION_PREFIX)) {
            throw new MessageDeliveryException("메시지를 보낼 수 없는 목적지입니다: " + destination);
        }
    }

    private static LoginMember loginMember(Principal user) {
        if (user instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginMember member) {
            return member;
        }
        throw new MessageDeliveryException("로그인이 필요합니다");
    }
}
