package kr.ject.naspi.common.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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

    @Test
    @DisplayName("카카오 인증에 실패하면 로그인 실패 경로로 이동한다")
    void kakaoLoginFailure() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/kakao")
                        .param("error", "access_denied")
                        .param("state", "unknown"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login-failure"));
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
