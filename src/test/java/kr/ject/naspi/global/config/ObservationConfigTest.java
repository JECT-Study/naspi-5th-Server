package kr.ject.naspi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void clear() {
        recorded.names.clear();
    }

    @Test
    @DisplayName("헬스체크·actuator 요청에서는 HTTP 요청·Security 관측(스팬)이 시작되지 않는다")
    void probesAreNotObserved() {
        call(port, "/livez");
        call(port, "/readyz");
        call(managementPort, "/actuator/health");
        call(managementPort, "/actuator/prometheus");
        call(port, "/actuator/health");

        // Redis 상태 확인 명령의 관측(lettuce)은 요청 스레드 밖에서 시작되어 요청과 연결할 수 없으므로 대상에서 뺀다.
        assertThat(recorded.names).filteredOn(name -> !name.equals("lettuce")).isEmpty();
    }

    @Test
    @DisplayName("일반 API 요청은 HTTP 요청·Security 관측(스팬)이 시작된다")
    void apiRequestIsObserved() {
        assertThat(call(port, "/api/members/me")).isEqualTo(401);

        assertThat(recorded.names).contains("http.server.requests", "spring.security.filterchains");
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

    @TestConfiguration
    static class RecordingConfig {

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }
}
