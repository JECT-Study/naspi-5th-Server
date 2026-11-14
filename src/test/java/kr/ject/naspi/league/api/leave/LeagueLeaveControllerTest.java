package kr.ject.naspi.league.api.leave;

import static org.mockito.ArgumentMatchers.anyLong;
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
import kr.ject.naspi.league.application.LeagueLeaveUseCase;
import kr.ject.naspi.league.application.LeftLeague;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ActiveProfiles("test")
@WebMvcTest(LeagueLeaveController.class)
@Import(SecurityConfig.class)
class LeagueLeaveControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueLeaveUseCase leagueLeaveUseCase;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("플레이어는 리그가 시작되기 전에 대기실을 나갈 수 있다")
    void leave() throws Exception {
        given(leagueLeaveUseCase.leave(MEMBER_ID, 3L)).willReturn(new LeftLeague(3L, false));

        mockMvc.perform(post("/api/leagues/3/leave").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.leagueId").value(3))
                .andExpect(jsonPath("$.data.waitingRoomDeleted").value(false));
    }

    @Test
    @DisplayName("리그가 시작된 이후에는 나갈 수 없다")
    void leaveAfterStart() throws Exception {
        given(leagueLeaveUseCase.leave(MEMBER_ID, 3L))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_STARTED));

        mockMvc.perform(post("/api/leagues/3/leave").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("208"));
    }

    @Test
    @DisplayName("로그인하지 않으면 리그를 나갈 수 없다")
    void leaveWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/leagues/3/leave"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(leagueLeaveUseCase).should(never()).leave(anyLong(), anyLong());
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
