package kr.ject.naspi.league.api.join;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.JoinedLeague;
import kr.ject.naspi.league.application.LeagueService;
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
@WebMvcTest(LeagueJoinController.class)
@Import(SecurityConfig.class)
class LeagueJoinControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueService leagueService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 초대 코드로 대기실에 입장할 수 있다")
    void join() throws Exception {
        given(leagueService.join(MEMBER_ID, "A1B2C3D")).willReturn(new JoinedLeague(3L, 9L));

        mockMvc.perform(post("/api/leagues/join")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \"A1B2C3D\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.leagueId").value(3));
    }

    @Test
    @DisplayName("유효하지 않은 초대 코드면 입장 실패 코드로 응답한다")
    void joinWithInvalidCode() throws Exception {
        given(leagueService.join(MEMBER_ID, "ZZZZZZZ"))
                .willThrow(new NaspiBizException(ErrorCode.INVITE_CODE_NOT_FOUND));

        mockMvc.perform(post("/api/leagues/join")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \"ZZZZZZZ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("201"));
    }

    @Test
    @DisplayName("초대 코드를 입력하지 않으면 입장할 수 없다")
    void joinWithoutCode() throws Exception {
        mockMvc.perform(post("/api/leagues/join")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("002"));

        then(leagueService).should(never()).join(anyLong(), anyString());
    }

    @Test
    @DisplayName("로그인하지 않으면 대기실에 입장할 수 없다")
    void joinWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/leagues/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \"A1B2C3D\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(leagueService).should(never()).join(anyLong(), anyString());
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
