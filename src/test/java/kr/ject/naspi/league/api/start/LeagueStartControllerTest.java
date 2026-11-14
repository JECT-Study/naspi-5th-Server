package kr.ject.naspi.league.api.start;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueStartUseCase;
import kr.ject.naspi.league.application.StartedLeague;
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
@WebMvcTest(LeagueStartController.class)
@Import(SecurityConfig.class)
class LeagueStartControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueStartUseCase leagueStartUseCase;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("방장은 리그를 시작하고 시작 일시를 받는다")
    void start() throws Exception {
        given(leagueStartUseCase.start(MEMBER_ID, 3L)).willReturn(
                new StartedLeague(3L, LocalDateTime.of(2026, 11, 13, 21, 0)));

        mockMvc.perform(post("/api/leagues/3/start").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.leagueId").value(3))
                .andExpect(jsonPath("$.data.startedAt").value("2026-11-13T21:00:00"));
    }

    @Test
    @DisplayName("방장이 아니면 시작 실패 코드로 응답한다")
    void startByNonHost() throws Exception {
        given(leagueStartUseCase.start(MEMBER_ID, 3L))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_HOST_ONLY));

        mockMvc.perform(post("/api/leagues/3/start").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("207"));
    }

    @Test
    @DisplayName("로그인하지 않으면 리그를 시작할 수 없다")
    void startWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/leagues/3/start"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(leagueStartUseCase).should(never()).start(anyLong(), anyLong());
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
