package kr.ject.naspi.websocket.api;

import java.util.List;
import kr.ject.naspi.common.security.CorsProperties;
import kr.ject.naspi.websocket.application.PeriodicTopic;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.application.TopicScheduler;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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

    /**
     * STOMP 엔드포인트 등록.
     *
     * @param registry 엔드포인트 등록
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    /**
     * 메시지 브로커 설정.
     *
     * @param registry 메시지 브로커 등록
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        long heartbeatMillis = properties.heartbeat().toMillis();
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {heartbeatMillis, heartbeatMillis})
                .setTaskScheduler(messageBrokerTaskScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * 클라이언트 인바운드 채널 설정.
     *
     * @param registration 채널 등록
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                new StompAuthChannelInterceptor(authorizers.orderedStream().toList()));
    }

    /**
     * 주기 발행 전용 스레드 풀. 다른 곳에서 {@link TaskScheduler}로 주입받지 않도록 후보에서 뺀다.
     *
     * @return 주기 발행 스케줄러
     */
    @Bean(autowireCandidate = false)
    public ThreadPoolTaskScheduler topicTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();  // Spring이 관리하는 스케줄러와 분리
        scheduler.setPoolSize(properties.schedulerPoolSize());              // 주기 발행 토픽이 많으면 늘린다
        scheduler.setThreadNamePrefix("ws-topic-");                         // 스레드 이름 접두사
        return scheduler;
    }

    /**
     * 주기 발행 토픽을 예약하는 스케줄러.
     *
     * @param topics 주기 발행 토픽
     * @param subscriptions 구독 현황
     * @param publisher 메시지 발행기
     * @return 주기 발행 스케줄러
     */
    @Bean
    public TopicScheduler topicScheduler(List<PeriodicTopic> topics,
            TopicSubscriptions subscriptions, TopicMessagePublisher publisher) {
        return new TopicScheduler(topicTaskScheduler(), topics, properties.intervals(),
                subscriptions, publisher);
    }
}
