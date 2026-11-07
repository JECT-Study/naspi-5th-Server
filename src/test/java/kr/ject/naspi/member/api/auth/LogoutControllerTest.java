package kr.ject.naspi.member.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.common.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(LogoutController.class)
@Import({SecurityConfig.class, LoginSession.class})
class LogoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그아웃하면 서비스 로그인 세션이 무효화되고, 이후 로그인이 필요한 API는 인증 필요로 응답한다")
    void logout() throws Exception {
        MockHttpSession session = loginSession();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"));

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/members/me/name").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));
    }

    @Test
    @DisplayName("로그인하지 않은 상태에서 로그아웃해도 성공으로 응답한다")
    void logoutWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"));
    }

    private MockHttpSession loginSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                        new LoginMember(7L), null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))));
        return session;
    }
}
