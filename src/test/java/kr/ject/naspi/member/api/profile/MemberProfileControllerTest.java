package kr.ject.naspi.member.api.profile;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.application.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ActiveProfiles("test")
@WebMvcTest(MemberProfileController.class)
@Import(SecurityConfig.class)
class MemberProfileControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 자신의 이름을 조회할 수 있다")
    void getName() throws Exception {
        given(memberService.getName(MEMBER_ID)).willReturn("홍길동");

        mockMvc.perform(get("/api/members/me/name").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.name").value("홍길동"));
    }

    @Test
    @DisplayName("로그인하지 않으면 이름을 조회할 수 없다")
    void getNameWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/members/me/name"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(memberService).should(never()).getName(anyLong());
    }

    @Test
    @DisplayName("로그인한 회원은 자신의 이름을 수정할 수 있다")
    void changeName() throws Exception {
        given(memberService.changeName(MEMBER_ID, "김철수")).willReturn("김철수");

        mockMvc.perform(patch("/api/members/me/name")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"김철수\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.name").value("김철수"));
    }

    @Test
    @DisplayName("이름 수정에도 회원가입과 같은 이름 규칙(최대 5자 한글)이 적용된다")
    void changeNameWithInvalidName() throws Exception {
        given(memberService.changeName(MEMBER_ID, "John"))
                .willThrow(new NaspiBizException(ErrorCode.INVALID_MEMBER_NAME));

        mockMvc.perform(patch("/api/members/me/name")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"John\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("101"));
    }

    @Test
    @DisplayName("이름을 보내지 않으면 400으로 응답한다")
    void changeNameWithoutName() throws Exception {
        mockMvc.perform(patch("/api/members/me/name")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("002"));
    }

    @Test
    @DisplayName("로그인하지 않으면 이름을 수정할 수 없다")
    void changeNameWithoutLogin() throws Exception {
        mockMvc.perform(patch("/api/members/me/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"김철수\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(memberService).should(never()).changeName(anyLong(), anyString());
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
