package kr.ject.naspi.websocket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.security.Principal;
import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class StompAuthChannelInterceptorTest {

    private static final Principal LOGIN_MEMBER = UsernamePasswordAuthenticationToken.authenticated(
            new LoginMember(7L), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

    private final MessageChannel channel = mock(MessageChannel.class);
    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(List.of(new LeagueOneAuthorizer()));

    @Test
    @DisplayName("로그인한 회원은 STOMP 연결할 수 있다")
    void connectWithLoginMember() {
        Message<?> message = frame(StompCommand.CONNECT, LOGIN_MEMBER, null);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("로그인 정보가 없으면 STOMP 연결을 거부한다")
    void connectWithoutLogin() {
        Message<?> message = frame(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("담당 Authorizer가 허용한 토픽은 구독할 수 있다")
    void subscribeAllowed() {
        Message<?> message =
                frame(StompCommand.SUBSCRIBE, LOGIN_MEMBER, "/topic/leagues/1/ranking");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("담당 Authorizer가 거부한 토픽은 구독할 수 없다")
    void subscribeDenied() {
        Message<?> message =
                frame(StompCommand.SUBSCRIBE, LOGIN_MEMBER, "/topic/leagues/2/ranking");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("담당 Authorizer가 없는 토픽은 구독할 수 없다")
    void subscribeWithoutAuthorizer() {
        Message<?> message = frame(StompCommand.SUBSCRIBE, LOGIN_MEMBER, "/topic/unknown");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("클라이언트는 브로커 토픽으로 직접 메시지를 보낼 수 없다")
    void sendToTopicDenied() {
        Message<?> message = frame(StompCommand.SEND, LOGIN_MEMBER, "/topic/leagues/1/ranking");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("클라이언트는 /app/ 목적지로 메시지를 보낼 수 있다")
    void sendToApplicationAllowed() {
        Message<?> message = frame(StompCommand.SEND, LOGIN_MEMBER, "/app/ping");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    private static Message<byte[]> frame(StompCommand command, Principal user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(user);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /** {@code /topic/leagues/1/}만 회원 7에게 허용하는 테스트 Authorizer. */
    private static class LeagueOneAuthorizer implements TopicSubscriptionAuthorizer {

        @Override
        public boolean supports(String destination) {
            return destination.startsWith("/topic/leagues/");
        }

        @Override
        public boolean canSubscribe(LoginMember member, String destination) {
            return member.memberId() == 7L && destination.startsWith("/topic/leagues/1/");
        }
    }
}
