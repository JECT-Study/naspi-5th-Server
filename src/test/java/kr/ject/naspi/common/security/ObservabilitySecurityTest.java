package kr.ject.naspi.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * 실제 서버를 띄워 헬스체크·메트릭 경로의 인증 규칙을 포트별로 검증한다.
 *
 * <p>테스트에는 Redis가 없으므로 Redis 세션 자동 설정을 뺀다. 메트릭 export는 테스트에서 기본으로 꺼지므로
 * Prometheus만 켠다.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.autoconfigure.exclude="
            + "org.springframework.boot.session.data.redis.autoconfigure"
            + ".SessionDataRedisAutoConfiguration",
    "management.prometheus.metrics.export.enabled=true",
})
class ObservabilitySecurityTest {

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Test
    @DisplayName("메인 포트의 /livez 는 인증 없이 200으로 응답한다")
    void livez() {
        assertThat(status(port, "/livez")).isEqualTo(200);
    }

    @Test
    @DisplayName("메인 포트의 /readyz 는 인증 없이 준비 상태(200 또는 503)를 응답한다")
    void readyz() {
        assertThat(status(port, "/readyz")).isIn(200, 503);
    }

    @Test
    @DisplayName("management 포트의 /actuator/health 는 인증 없이 상태(200 또는 503)를 응답한다")
    void healthOnManagementPort() {
        assertThat(status(managementPort, "/actuator/health")).isIn(200, 503);
    }

    @Test
    @DisplayName("management 포트의 /actuator/prometheus 는 인증 없이 200으로 응답한다")
    void prometheusOnManagementPort() {
        assertThat(status(managementPort, "/actuator/prometheus")).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/prometheus", "/actuator"})
    @DisplayName("외부에 공개된 메인 포트에서는 actuator 엔드포인트에 접근할 수 없다")
    void actuatorOnMainPort(String path) {
        assertThat(status(port, path)).isIn(401, 404);
    }

    @Test
    @DisplayName("메인 포트의 다른 경로는 여전히 로그인을 요구한다")
    void otherPathsStillRequireLogin() {
        assertThat(status(port, "/api/members/me")).isEqualTo(401);
    }

    private int status(int targetPort, String path) {
        return RestClient.create("http://localhost:" + targetPort).get().uri(path)
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
