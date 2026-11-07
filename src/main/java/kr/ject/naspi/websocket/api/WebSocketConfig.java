package kr.ject.naspi.websocket.api;

import kr.ject.naspi.common.security.CorsProperties;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 *
 * <pre>
 * 연결: ws(s)://{host}/ws  세션 쿠키 필요. 로그인하지 않으면 핸드셰이크가 401로 거부된다
 * 구독: /topic/**          서버 → 클라이언트 브로드캐스트 (내장 SimpleBroker)
 * 전송: /app/**            클라이언트 → 서버 (@MessageMapping)
 * </pre>
 *
 * <p>내장 브로커는 서버 메모리 안에서만 동작하므로 서버 한 대를 전제로 한다.
 * 서버를 여러 대로 늘리면 {@link TopicMessagePublisher}를 Redis Pub/Sub 구현으로 교체한다.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** STOMP 연결 엔드포인트. */
    public static final String ENDPOINT = "/ws";

    private final WebSocketProperties properties;
    private final CorsProperties corsProperties;
    private final ObjectProvider<TopicSubscriptionAuthorizer> authorizers;
    private final TaskScheduler messageBrokerTaskScheduler;

    /**
     * 설정을 만든다.
     *
     * @param properties 실시간 메시지 설정
     * @param corsProperties 허용 출처 (핸드셰이크 Origin 검사에 사용)
     * @param authorizers 토픽 구독 권한 판단
     * @param messageBrokerTaskScheduler heartbeat에 쓰는 브로커 스케줄러 (Spring이 등록)
     */
    public WebSocketConfig(WebSocketProperties properties, CorsProperties corsProperties,
            ObjectProvider<TopicSubscriptionAuthorizer> authorizers,
            @Lazy TaskScheduler messageBrokerTaskScheduler) {
        this.properties = properties;
        this.corsProperties = corsProperties;
        this.authorizers = authorizers;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        long heartbeatMillis = properties.heartbeat().toMillis();
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {heartbeatMillis, heartbeatMillis})
                .setTaskScheduler(messageBrokerTaskScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                new StompAuthChannelInterceptor(authorizers.orderedStream().toList()));
    }
}
