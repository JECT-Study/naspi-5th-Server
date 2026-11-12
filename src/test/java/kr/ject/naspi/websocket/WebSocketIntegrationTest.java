package kr.ject.naspi.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.websocket.application.PeriodicTopic;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실제 서버에 STOMP로 연결해 인증, 이벤트 발행, 주기 발행을 검증한다.
 *
 * <p>테스트에는 Redis가 없으므로 Redis 세션 대신 메모리 세션 저장소를 쓴다.
 * 로그인은 테스트 전용 경로에서 실제 {@link LoginSession}으로 처리해 세션 쿠키를 받는다.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.autoconfigure.exclude="
            + "org.springframework.boot.session.data.redis.autoconfigure"
            + ".SessionDataRedisAutoConfiguration",
    "naspi.websocket.topics.test-periodic.interval=100ms",
})
@Import(WebSocketIntegrationTest.TestTopics.class)
class WebSocketIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String PROBE = "PROBE";

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TopicSubscriptions subscriptions;

    @Autowired
    private TopicMessagePublisher publisher;

    @Autowired
    private TestTopics testTopics;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 WebSocket에 연결할 수 없다")
    void rejectsUnauthenticatedHandshake() {
        var future = stompClient.connectAsync(url(), new WebSocketHttpHeaders(),
                new StompSessionHandlerAdapter() {});

        assertThatThrownBy(() -> future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause().hasMessageContaining("401");
    }

    @Test
    @DisplayName("로그인한 사용자는 세션 쿠키로 연결하고, 서버가 heartbeat 간격을 알려준다")
    void connectsWithSessionAndHeartbeat() throws Exception {
        BlockingQueue<StompHeaders> connected = new LinkedBlockingQueue<>();
        session = stompClient.connectAsync(url(), cookieHeaders(login(7L)), new StompHeaders(),
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
                        connected.add(headers);
                    }
                }).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        StompHeaders headers = connected.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(headers).isNotNull();
        assertThat(headers.getHeartbeat()).containsExactly(10_000L, 10_000L);
    }

    @Test
    @DisplayName("Spring 이벤트가 발생하면 이벤트에 지정된 토픽으로 메시지가 즉시 전달된다")
    void eventIsDeliveredToTopic() throws Exception {
        session = connect(7L);
        BlockingQueue<Map<String, Object>> received = subscribe("/topic/test/events/a");

        eventPublisher.publishEvent(new TestEvent("a", "체결"));

        Map<String, Object> message = received.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message).containsEntry("type", "TEST_EVENT").containsEntry("payload", "체결");
        assertThat(message.get("publishedAt")).isInstanceOf(Long.class);
    }

    @Test
    @DisplayName("서로 다른 이벤트는 서로 다른 토픽으로 전달되고, 다른 토픽 구독자는 받지 않는다")
    void differentEventsGoToDifferentTopics() throws Exception {
        session = connect(7L);
        BlockingQueue<Map<String, Object>> topicA = subscribe("/topic/test/events/a");
        final BlockingQueue<Map<String, Object>> topicB = subscribe("/topic/test/events/b");

        eventPublisher.publishEvent(new TestEvent("a", "A 이벤트"));
        eventPublisher.publishEvent(new TestEvent("b", "B 이벤트"));

        assertThat(topicA.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .containsEntry("payload", "A 이벤트");
        assertThat(topicB.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .containsEntry("payload", "B 이벤트");
        assertThat(topicA.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(topicB.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("이벤트 발행과 주기 발행이 동시에 동작하고, 주기 설정이 없는 토픽은 실행되지 않는다")
    void eventAndPeriodicPublishingWorkTogether() throws Exception {
        session = connect(7L);
        final BlockingQueue<Map<String, Object>> periodic = subscribe("/topic/test/periodic");
        BlockingQueue<Map<String, Object>> events = subscribe("/topic/test/events/a");
        subscribe("/topic/test/unscheduled");

        eventPublisher.publishEvent(new TestEvent("a", "체결"));

        assertThat(events.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .containsEntry("payload", "체결");
        assertThat(periodic.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .containsEntry("type", "TEST_PERIODIC");
        assertThat(periodic.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .containsEntry("type", "TEST_PERIODIC");
        assertThat(testTopics.unscheduledProduced.get()).isZero();
    }

    private StompSession connect(long memberId) throws Exception {
        return stompClient.connectAsync(url(), cookieHeaders(login(memberId)),
                        new StompSessionHandlerAdapter() {})
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 토픽을 구독하고, 브로커에 구독이 등록될 때까지 기다린다.
     *
     * <p>구독은 비동기로 처리되므로 확인용 메시지(PROBE)를 받을 때까지 반복 발행한다.
     * 확인용 메시지는 받은 메시지 목록에 넣지 않는다.
     */
    private BlockingQueue<Map<String, Object>> subscribe(String destination) throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        CountDownLatch ready = new CountDownLatch(1);
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> message = (Map<String, Object>) payload;
                if (PROBE.equals(message.get("type"))) {
                    ready.countDown();
                    return;
                }
                received.add(message);
            }
        });
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!ready.await(50, TimeUnit.MILLISECONDS)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("구독이 등록되지 않음: " + destination);
            }
            publisher.publish(new TopicMessage(destination, PROBE, null));
        }
        assertThat(subscriptions.hasSubscribers(destination)).isTrue();
        return received;
    }

    private String login(long memberId) {
        HttpHeaders headers = RestClient.create("http://localhost:" + port)
                .post()
                .uri(uri -> uri.path("/test/login").queryParam("memberId", memberId).build())
                .retrieve()
                .toBodilessEntity()
                .getHeaders();
        String setCookie = headers.getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.split(";", 2)[0];
    }

    private static WebSocketHttpHeaders cookieHeaders(String cookie) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    private String url() {
        return "ws://localhost:" + port + "/ws";
    }

    /**
     * 테스트 이벤트.
     *
     * @param channel 발행할 토픽 구분
     * @param value 전달할 값
     */
    record TestEvent(String channel, String value) {
    }

    /** 테스트용 토픽 구현과 로그인 경로. 실제 토픽 구현과 같은 방식(빈 추가)으로 등록한다. */
    @TestConfiguration
    @EnableSpringHttpSession
    static class TestTopics {

        final AtomicInteger unscheduledProduced = new AtomicInteger();

        @Bean
        MapSessionRepository sessionRepository() {
            return new MapSessionRepository(new ConcurrentHashMap<>());
        }

        @Bean
        @Order(0)
        SecurityFilterChain testLoginFilterChain(HttpSecurity http) {
            http.securityMatcher("/test/login")
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable);
            return http.build();
        }

        @Bean
        TestLoginController testLoginController(LoginSession loginSession) {
            return new TestLoginController(loginSession);
        }

        @Bean
        TestEventTopicListener testEventTopicListener(TopicMessagePublisher publisher) {
            return new TestEventTopicListener(publisher);
        }

        @Bean
        TopicSubscriptionAuthorizer testTopicAuthorizer() {
            return new TopicSubscriptionAuthorizer() {
                @Override
                public boolean supports(String destination) {
                    return destination.startsWith("/topic/test/");
                }

                @Override
                public boolean canSubscribe(LoginMember member, String destination) {
                    return true;
                }
            };
        }

        @Bean
        PeriodicTopic testPeriodicTopic() {
            AtomicInteger count = new AtomicInteger();
            return new PeriodicTopic() {
                @Override
                public String name() {
                    return "test-periodic";
                }

                @Override
                public String destinationPrefix() {
                    return "/topic/test/periodic";
                }

                @Override
                public List<TopicMessage> produce() {
                    return List.of(new TopicMessage("/topic/test/periodic", "TEST_PERIODIC",
                            count.incrementAndGet()));
                }
            };
        }

        /** 주기 설정이 없으므로 구독자가 있어도 실행되면 안 되는 토픽. */
        @Bean
        PeriodicTopic testUnscheduledTopic() {
            return new PeriodicTopic() {
                @Override
                public String name() {
                    return "test-unscheduled";
                }

                @Override
                public String destinationPrefix() {
                    return "/topic/test/unscheduled";
                }

                @Override
                public List<TopicMessage> produce() {
                    unscheduledProduced.incrementAndGet();
                    return List.of();
                }
            };
        }
    }

    /** 실제 토픽 리스너와 같은 형태: 커밋 후 도메인 이벤트를 토픽 메시지로 바꿔 발행한다. */
    static class TestEventTopicListener {

        private final TopicMessagePublisher publisher;

        TestEventTopicListener(TopicMessagePublisher publisher) {
            this.publisher = publisher;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
        void on(TestEvent event) {
            publisher.publish(new TopicMessage("/topic/test/events/" + event.channel(),
                    "TEST_EVENT", event.value()));
        }
    }

    /** 테스트 전용 로그인. 실제 카카오 로그인과 같은 {@link LoginSession}으로 세션에 로그인 정보를 저장한다. */
    @RestController
    static class TestLoginController {

        private final LoginSession loginSession;

        TestLoginController(LoginSession loginSession) {
            this.loginSession = loginSession;
        }

        @PostMapping("/test/login")
        void login(@RequestParam long memberId, HttpServletRequest request,
                HttpServletResponse response) {
            loginSession.login(request, response, memberId);
        }
    }
}
