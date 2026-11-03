package kr.ject.naspi.common.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 기본 폼 로그인과 카카오 로그인 진입이 동작하는지 검증한다.
 *
 * <p>폼 로그인 계정은 application.yaml의 임시 계정({@code spring.security.user})을 사용한다.
 * 카카오 로그인은 외부 서버가 필요하므로 카카오 인가 페이지로 이동하는 것까지만 확인한다.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${spring.security.user.name}")
    private String username;

    @Value("${spring.security.user.password}")
    private String password;

    @Test
    @DisplayName("로그인하지 않고 요청하면 기본 로그인 페이지로 이동한다")
    void redirectToLoginPage() throws Exception {
        mockMvc.perform(get("/any-page"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("공개 경로는 로그인하지 않아도 로그인 페이지로 이동하지 않는다")
    void publicPathWithoutLogin() throws Exception {
        // 아직 공개 API가 없으므로 보안을 통과해 404가 반환되는지로 확인한다.
        mockMvc.perform(get("/public/any"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("로그인이 필요한 경로는 로그인하지 않으면 로그인 페이지로 이동한다")
    void privatePathWithoutLogin() throws Exception {
        mockMvc.perform(get("/private/any"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("올바른 계정으로 로그인하면 인증된다")
    void loginSuccess() throws Exception {
        login(password).andExpect(authenticated().withUsername(username));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 로그인 페이지로 되돌아간다")
    void loginFailure() throws Exception {
        login("wrong-password")
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("기본 로그인 페이지에 카카오 로그인 링크가 표시된다")
    void loginPageHasKakaoLink() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/kakao")));
    }

    @Test
    @DisplayName("카카오 로그인을 요청하면 카카오 인가 페이지로 이동한다")
    void kakaoLoginRedirectsToKakao() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        startsWith("https://kauth.kakao.com/oauth/authorize")))
                .andExpect(header().string(HttpHeaders.LOCATION,
                        containsString("redirect_uri=http://localhost/login/oauth2/code/kakao")));
    }

    /**
     * 기본 로그인 폼과 같은 형식으로 로그인을 요청한다.
     */
    private ResultActions login(String inputPassword) throws Exception {
        return mockMvc.perform(post("/login")
                .param("username", username)
                .param("password", inputPassword)
                .with(csrf()));
    }
}
