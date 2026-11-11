package kr.ject.naspi.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 경로, 비인증 응답, 카카오 로그인 진입·실패 처리, CORS 허용 출처를 검증한다.
 *
 * <p>카카오 로그인은 외부 서버가 필요하므로 카카오 인가 페이지로 이동하는 것과 실패 처리까지만 확인한다.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = SecurityConfigTest.PrivateController.class)
@Import({SecurityConfig.class, LoginSession.class, SecurityConfigTest.PrivateController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("서비스는 회원만 이용 가능: 로그인하지 않고 요청하면 401과 인증 필요 결과 코드로 응답한다")
    void unauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));
    }

    @Test
    @DisplayName("로그인한 회원은 요청에서 자신의 회원 ID를 주입받는다")
    void authenticatedRequest() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated(new LoginMember(7L), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        mockMvc.perform(get("/private").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(7));
    }

    @Test
    @DisplayName("카카오 로그인을 요청하면 닉네임만 요청하는 카카오 인가 페이지로 이동한다")
    void kakaoLoginRedirectsToKakao() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("https://kauth.kakao.com/oauth/authorize")))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        containsString("scope=profile_nickname")))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        containsString("redirect_uri=http://localhost/login/oauth2/code/kakao")));
    }

    @Test
    @DisplayName("허용된 프론트 출처의 사전 요청(preflight)은 쿠키를 포함한 호출을 허용한다")
    void corsPreflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/private")
                        .header(HttpHeaders.ORIGIN, "https://front.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://front.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용되지 않은 출처의 사전 요청(preflight)은 거부한다")
    void corsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/private")
                        .header(HttpHeaders.ORIGIN, "https://evil.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    @DisplayName("API는 GET, POST만 쓰므로 PUT, PATCH, DELETE 사전 요청(preflight)은 거부한다")
    void corsPreflightWithDisallowedMethod(String method) throws Exception {
        mockMvc.perform(options("/private")
                        .header(HttpHeaders.ORIGIN, "https://front.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/v3/api-docs"})
    @DisplayName("Swagger UI와 API 문서는 계정 없이 접근하면 HTTP Basic 로그인을 요구한다")
    void swaggerRequiresLogin(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Basic")));
    }

    @Test
    @DisplayName("Swagger 계정 비밀번호가 틀리면 접근할 수 없다")
    void swaggerWithWrongPassword() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(httpBasic("swagger-test", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Swagger 계정으로 로그인하면 접근할 수 있고 세션은 만들지 않는다")
    void swaggerWithLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(httpBasic("swagger-test", "swagger-test-pw")))
                .andExpect(result -> {
                    assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
                    assertThat(result.getRequest().getSession(false)).isNull();
                });
    }

    @Test
    @DisplayName("Swagger 계정으로는 회원 API를 호출할 수 없다")
    void swaggerAccountCannotCallMemberApi() throws Exception {
        mockMvc.perform(get("/private").with(httpBasic("swagger-test", "swagger-test-pw")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));
    }

    @Test
    @DisplayName("카카오 인증에 실패하면 로그인 실패 경로로 이동한다")
    void kakaoLoginFailure() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/kakao")
                        .param("error", "access_denied")
                        .param("state", "unknown"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://front.test/login-failure"));
    }

    @Test
    @DisplayName("허용된 프론트 출처에서 카카오 로그인을 시작하면 로그인 후 그 출처로 돌아간다")
    void kakaoLoginReturnsToStartedOrigin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/oauth2/authorization/kakao")
                        .session(session)
                        .header(HttpHeaders.REFERER, "https://local-front.test:5173/login"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/login/oauth2/code/kakao")
                        .session(session)
                        .param("error", "access_denied")
                        .param("state", "unknown"))
                .andExpect(redirectedUrl("https://local-front.test:5173/login-failure"));
    }

    @Test
    @DisplayName("허용되지 않은 출처에서 카카오 로그인을 시작하면 로그인 후 기본 프론트 출처로 이동한다")
    void kakaoLoginFromNotAllowedOrigin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/oauth2/authorization/kakao")
                        .session(session)
                        .header(HttpHeaders.REFERER, "https://evil.test/"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/login/oauth2/code/kakao")
                        .session(session)
                        .param("error", "access_denied")
                        .param("state", "unknown"))
                .andExpect(redirectedUrl("https://front.test/login-failure"));
    }

    /**
     * 로그인이 필요한 경로 예시.
     */
    @RestController
    static class PrivateController {

        @GetMapping("/private")
        long privatePath(@AuthenticationPrincipal LoginMember loginMember) {
            return loginMember.memberId();
        }
    }
}
