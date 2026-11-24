package kr.ject.naspi.member.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.application.MemberAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.ResultActions;

@ActiveProfiles("test")
@WebMvcTest(MockLoginController.class)
@Import({SecurityConfig.class, LoginSession.class})
class MockLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberAuthService memberAuthService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("처음 쓰는 테스트 회원 번호면 음수 카카오 회원 번호로 가입하고 7일간 로그인된다")
    void joinAndLogin() throws Exception {
        given(memberAuthService.findMemberId(-1L)).willReturn(Optional.empty());
        given(memberAuthService.joinWithInputName(-1L, "방장")).willReturn(7L);

        MvcResult result = mockLogin("{\"userNo\": 1, \"name\": \"방장\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andReturn();

        assertLoggedIn(result, 7L);
    }

    @Test
    @DisplayName("이미 가입한 테스트 회원이면 다시 가입하지 않고 같은 회원으로 로그인된다")
    void loginExisting() throws Exception {
        given(memberAuthService.findMemberId(-1L)).willReturn(Optional.of(7L));

        MvcResult result = mockLogin("{\"userNo\": 1, \"name\": \"새이름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(7))
                .andReturn();

        assertLoggedIn(result, 7L);
        then(memberAuthService).should(never()).joinWithInputName(anyLong(), any());
    }

    @Test
    @DisplayName("이름을 보내지 않으면 기본 이름으로 가입한다")
    void joinWithDefaultName() throws Exception {
        given(memberAuthService.findMemberId(-1000L)).willReturn(Optional.empty());
        given(memberAuthService.joinWithInputName(-1000L, "테스터")).willReturn(8L);

        mockLogin("{\"userNo\": 1000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(8));
    }

    @Test
    @DisplayName("처음 쓰는 번호인데 이름이 규칙에 맞지 않으면 로그인되지 않는다")
    void joinWithInvalidName() throws Exception {
        given(memberAuthService.findMemberId(-1L)).willReturn(Optional.empty());
        given(memberAuthService.joinWithInputName(-1L, "John"))
                .willThrow(new NaspiBizException(ErrorCode.INVALID_MEMBER_NAME));

        MvcResult result = mockLogin("{\"userNo\": 1, \"name\": \"John\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("101"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"userNo\": 0}", "{\"userNo\": -1}", "{\"userNo\": 1001}"})
    @DisplayName("테스트 회원 번호가 없거나 1~1000이 아니면 400으로 응답한다 (실제 카카오 회원 번호를 만들 수 없다)")
    void invalidUserNo(String body) throws Exception {
        mockLogin(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("002"));

        then(memberAuthService).shouldHaveNoInteractions();
    }

    private ResultActions mockLogin(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/mock-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assertLoggedIn(MvcResult result, long memberId) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal())
                .isEqualTo(new LoginMember(memberId));
        assertThat(session.getMaxInactiveInterval()).isEqualTo(7 * 24 * 60 * 60);
    }
}
