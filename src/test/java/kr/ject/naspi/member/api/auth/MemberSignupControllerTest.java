package kr.ject.naspi.member.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.BusinessException;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.member.application.MemberAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@WebMvcTest(MemberSignupController.class)
@Import({SecurityConfig.class, LoginSession.class})
class MemberSignupControllerTest {

    private static final long KAKAO_ID = 12345L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberAuthService memberAuthService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("카카오 이름에 한글이 없는 사용자가 이름을 입력해 가입하면 가입 대기 상태가 끝나고 회원으로 7일간 로그인된다")
    void signup() throws Exception {
        given(memberAuthService.joinWithInputName(KAKAO_ID, "존")).willReturn(7L);
        MockHttpSession session = pendingSession();

        MvcResult result = mockMvc.perform(post("/api/members/signup")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"존\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20000))
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andReturn();

        MockHttpSession saved = (MockHttpSession) result.getRequest().getSession(false);
        SecurityContext context = (SecurityContext) saved.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal()).isEqualTo(new LoginMember(7L));
        assertThat(saved.getAttribute(SignupSession.ATTRIBUTE)).isNull();
        assertThat(saved.getMaxInactiveInterval()).isEqualTo(7 * 24 * 60 * 60);
    }

    @Test
    @DisplayName("이름이 규칙(최대 5자 한글)에 맞지 않으면 가입되지 않고 가입 대기 상태가 유지된다")
    void signupWithInvalidName() throws Exception {
        given(memberAuthService.joinWithInputName(KAKAO_ID, "John"))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT,
                        "이름은 1~5자의 한글만 사용할 수 있습니다."));
        MockHttpSession session = pendingSession();

        mockMvc.perform(post("/api/members/signup")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"John\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));

        assertThat(session.getAttribute(SignupSession.ATTRIBUTE)).isNotNull();
    }

    @Test
    @DisplayName("이름을 보내지 않으면 400으로 응답한다")
    void signupWithoutName() throws Exception {
        mockMvc.perform(post("/api/members/signup")
                        .session(pendingSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    @DisplayName("카카오 로그인을 하지 않고 가입을 요청하면 인증 필요로 응답한다")
    void signupWithoutKakaoLogin() throws Exception {
        mockMvc.perform(post("/api/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"존\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private MockHttpSession pendingSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SignupSession.ATTRIBUTE,
                new PendingSignup(KAKAO_ID));
        return session;
    }
}
