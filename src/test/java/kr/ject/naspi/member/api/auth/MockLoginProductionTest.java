package kr.ject.naspi.member.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * 운영 프로필로 띄운 실제 서버에서 모킹 로그인을 쓸 수 없는지 검증한다.
 *
 * <p>test 프로필은 DB 등 접속 설정을 위해 함께 켠다. 허용 프로필이 있어도 production이 있으면 막혀야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "production"})
class MockLoginProductionTest {

    @Autowired
    private ApplicationContext context;

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/auth/mock-login",
        "/api/auth/mock%2Dlogin",
    })
    @DisplayName("운영에서는 모킹 로그인이 등록되지 않고, 요청하면 세션 없이 404로 끝난다")
    void blockedInProduction(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // 키가 맞아도 막혀야 한다
                .header("X-Mock-Login-Key", "mock-login-test-key")
                .POST(HttpRequest.BodyPublishers.ofString("{\"userNo\": 1}"))
                .build();

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(context.getBeansOfType(MockLoginController.class)).isEmpty();
    }
}
