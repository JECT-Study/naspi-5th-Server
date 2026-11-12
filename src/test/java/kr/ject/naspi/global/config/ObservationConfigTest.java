package kr.ject.naspi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import kr.ject.naspi.global.logging.WebLoggingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * 실제 서버를 띄워 헬스체크·actuator 요청에서는 관측(스팬)이 시작되지 않는지 검증한다.
 *
 * <p>시작된 관측은 테스트용 {@link ObservationHandler}로 기록한다. 스팬은 관측이 시작될 때 만들어지므로
 * 기록된 관측이 없으면 스팬도 없다. Security·JDBC 관측도 여기에 기록된다.
 *
 * <p>Redis 명령 관측({@code lettuce})은 시작 단계에서 막지 않고 내보내기 단계에서 거른다. 그래서 로컬에 Redis가
 * 떠 있으면 헬스체크에서도 lettuce 관측이 시작되므로, 관측 단계 단언에서는 lettuce를 빼고 내보낸 스팬으로
 * 검증한다. 내보낸 스팬은 테스트용 {@link SpanExporter}로 기록하며, 내보내기 필터를 거친 스팬만 여기에 온다.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.autoconfigure.exclude="
            + "org.springframework.boot.session.data.redis.autoconfigure"
            + ".SessionDataRedisAutoConfiguration",
    "management.prometheus.metrics.export.enabled=true",
})
class ObservationConfigTest {

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private RecordingHandler recorded;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired
    private RecordingExporter exported;

    @Autowired
    private Tracer tracer;

    @Autowired
    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void clear() {
        flush();
        recorded.names.clear();
        exported.spans.clear();
    }

    @Test
    @DisplayName("헬스체크·actuator 요청에서는 관측이 시작되지 않고, 내보내는 스팬이 하나도 없다")
    void probesAreNotObserved() {
        call(port, "/livez");
        call(port, "/readyz");
        call(managementPort, "/actuator/health");
        call(managementPort, "/actuator/prometheus");
        call(port, "/actuator/health");
        flush();

        assertThat(recorded.names).filteredOn(name -> !name.equals("lettuce")).isEmpty();
        assertThat(exported.spans).isEmpty();
    }

    @Test
    @DisplayName("일반 API 요청은 HTTP 요청·Security 관측(스팬)이 시작된다")
    void apiRequestIsObserved() {
        assertThat(call(port, "/api/members/me")).isEqualTo(401);

        assertThat(recorded.names).contains("http.server.requests", "spring.security.filterchains");
    }

    @Test
    @DisplayName("인증 실패로 막힌 요청도 요청·응답 로그가 남고, 로그에 traceId가 붙는다")
    void requestLogHasTraceId() {
        Logger logger = (Logger) LoggerFactory.getLogger(WebLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(call(port, "/api/members/me")).isEqualTo(401);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getFormattedMessage())
                .startsWith("[REQUEST] GET /api/members/me");
        assertThat(appender.list.get(1).getFormattedMessage())
                .startsWith("[RESPONSE] GET /api/members/me 401");
        assertThat(appender.list)
                .allSatisfy(event -> assertThat(event.getMDCPropertyMap()).containsKey("traceId"));
    }

    @Test
    @DisplayName("헬스체크의 DB 상태 확인은 JDBC 관측(스팬)을 남기지 않는다")
    void probeDatabaseCheckIsNotObserved() {
        call(port, "/readyz");
        call(managementPort, "/actuator/health");
        call(managementPort, "/actuator/health/readiness");

        assertThat(recorded.names).noneMatch(name -> name.startsWith("jdbc."));
    }

    @Test
    @DisplayName("DB 쿼리는 요청 밖에서도, 관측 중인 작업 안에서도 JDBC 관측(스팬)이 시작된다")
    void queryIsObserved() {
        jdbcTemplate.queryForObject("select 1", Integer.class);

        assertThat(recorded.names)
                .containsExactly("jdbc.connection", "jdbc.query", "jdbc.result-set");

        recorded.names.clear();
        Observation.createNotStarted("test.parent", observationRegistry)
                .observe(() -> jdbcTemplate.queryForObject("select 1", Integer.class));

        assertThat(recorded.names)
                .containsExactly("test.parent", "jdbc.connection", "jdbc.query", "jdbc.result-set");
    }

    @Test
    @DisplayName("부모 없는 Redis 명령 스팬은 내보내지 않고, 부모 있는 Redis 명령 스팬과 부모 없는 다른 스팬은 내보낸다")
    void rootRedisSpanIsNotExported() {
        // lettuce가 만드는 스팬과 같은 모양(이름은 명령어, 태그 db.system=redis)으로 만든다.
        tracer.nextSpan().name("info").tag("db.system", "redis").start().end();
        Span parent = tracer.nextSpan().name("test.parent").start();
        tracer.nextSpan(parent).name("hgetall").tag("db.system", "redis").start().end();
        parent.end();
        tracer.nextSpan().name("test.root").tag("db.system", "h2").start().end();
        flush();

        assertThat(exported.spans).extracting(SpanData::getName)
                .contains("test.parent", "hgetall", "test.root").doesNotContain("info");
    }

    private void flush() {
        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
    }

    private int call(int targetPort, String path) {
        return RestClient.create("http://localhost:" + targetPort).get().uri(path)
                .exchange((request, response) -> response.getStatusCode().value());
    }

    /** 시작된 관측의 이름을 기록한다. 막힌 관측은 핸들러까지 오지 않는다. */
    static class RecordingHandler implements ObservationHandler<Observation.Context> {

        final List<String> names = new CopyOnWriteArrayList<>();

        @Override
        public void onStart(Observation.Context context) {
            names.add(context.getName());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

    /** 내보내기 필터를 통과해 실제로 내보내는 스팬을 기록한다. */
    static class RecordingExporter implements SpanExporter {

        final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }

    @TestConfiguration
    static class RecordingConfig {

        @Bean
        RecordingExporter recordingExporter() {
            return new RecordingExporter();
        }

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }
}
