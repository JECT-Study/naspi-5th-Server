package kr.ject.naspi.member.api.withdraw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.application.MemberService;
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
@WebMvcTest(MemberWithdrawController.class)
@Import({SecurityConfig.class, LoginSession.class})
class MemberWithdrawControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("회원은 즉시 탈퇴할 수 있고, 탈퇴하면 로그아웃된다")
    void withdraw() throws Exception {
        MockHttpSession session = loginSession();

        mockMvc.perform(post("/api/members/me/withdraw").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"));

        then(memberService).should().withdraw(MEMBER_ID);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("회원 정보가 없으면 회원 없음으로 응답한다")
    void withdrawUnknownMember() throws Exception {
        willThrow(new NaspiBizException(ErrorCode.MEMBER_NOT_FOUND))
                .given(memberService).withdraw(MEMBER_ID);

        mockMvc.perform(post("/api/members/me/withdraw").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("103"));
    }

    @Test
    @DisplayName("로그인하지 않으면 탈퇴할 수 없다")
    void withdrawWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/members/me/withdraw"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(memberService).should(never()).withdraw(anyLong());
    }

    private MockHttpSession loginSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                        new LoginMember(MEMBER_ID), null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))));
        return session;
    }
}
