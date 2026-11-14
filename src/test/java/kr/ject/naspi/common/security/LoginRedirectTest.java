package kr.ject.naspi.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginRedirectTest {

    private static final String DEFAULT_FRONT = "https://www.naspi.my";
    private static final String LOCAL_FRONT = "https://local.naspi.my:5173";

    private final LoginRedirect loginRedirect = new LoginRedirect(
            new CorsProperties(List.of(DEFAULT_FRONT, LOCAL_FRONT)));

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("로그인을 시작한 프론트 출처가 허용 출처면 로그인 후 그 출처로 이동한다")
    void allowedOrigin() throws Exception {
        request.addHeader(HttpHeaders.REFERER, LOCAL_FRONT + "/login?from=home");

        loginRedirect.saveOrigin(request);
        loginRedirect.redirect(request, response, "/signup");

        assertThat(response.getRedirectedUrl()).isEqualTo(LOCAL_FRONT + "/signup");
        assertThat(request.getSession().getAttribute(LoginRedirect.ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("허용되지 않은 출처에서 로그인을 시작하면 기본 프론트 출처로 이동한다")
    void notAllowedOrigin() throws Exception {
        request.addHeader(HttpHeaders.REFERER, "https://evil.test/");

        loginRedirect.saveOrigin(request);
        loginRedirect.redirect(request, response, "/");

        assertThat(response.getRedirectedUrl()).isEqualTo(DEFAULT_FRONT + "/");
    }

    @Test
    @DisplayName("출처를 알 수 없으면(직접 접속) 기본 프론트 출처로 이동한다")
    void noReferer() throws Exception {
        loginRedirect.saveOrigin(request);
        loginRedirect.redirect(request, response, "/login-failure");

        assertThat(response.getRedirectedUrl()).isEqualTo(DEFAULT_FRONT + "/login-failure");
    }

    @Test
    @DisplayName("허용 출처와 포트가 다르면 다른 출처로 보고 기본 프론트 출처로 이동한다")
    void differentPort() throws Exception {
        request.addHeader(HttpHeaders.REFERER, "https://local.naspi.my:3000/");

        loginRedirect.saveOrigin(request);
        loginRedirect.redirect(request, response, "/");

        assertThat(response.getRedirectedUrl()).isEqualTo(DEFAULT_FRONT + "/");
    }
}
