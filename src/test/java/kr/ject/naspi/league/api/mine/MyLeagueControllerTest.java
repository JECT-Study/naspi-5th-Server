package kr.ject.naspi.league.api.mine;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueTradingDay;
import kr.ject.naspi.league.application.MyLeague;
import kr.ject.naspi.league.domain.LeagueStatus;
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
@WebMvcTest(MyLeagueController.class)
@Import(SecurityConfig.class)
class MyLeagueControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 15, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 자신이 입장한 리그 목록을 상태 문자열과 함께 조회할 수 있다")
    void getMyLeagues() throws Exception {
        LocalDateTime endsAt = LocalDateTime.of(2026, 11, 23, 9, 0);
        given(leagueQueryService.getMyLeagues(MEMBER_ID)).willReturn(List.of(
                new MyLeague(2L, "진행", LeagueStatus.STARTED, 3, 3, true, true, NOW, NOW, null,
                        NOW, endsAt, new LeagueTradingDay(1, 7,
                                LocalDateTime.of(2026, 11, 17, 9, 0), NOW, endsAt)),
                new MyLeague(1L, "대기", LeagueStatus.WAITING, 2, 1, false, false, NOW, NOW,
                        NOW.plusDays(7), null, null, null)));

        mockMvc.perform(get("/api/leagues/me").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data[0].leagueId").value(2))
                .andExpect(jsonPath("$.data[0].status").value("STARTED"))
                .andExpect(jsonPath("$.data[0].host").value(true))
                .andExpect(jsonPath("$.data[0].waitingRoomExpiresAt").isEmpty())
                .andExpect(jsonPath("$.data[0].tradingDay.dayNumber").value(1))
                .andExpect(jsonPath("$.data[0].tradingDay.totalDays").value(7))
                .andExpect(jsonPath("$.data[0].tradingDay.dayEndsAt")
                        .value("2026-11-17T09:00:00"))
                .andExpect(jsonPath("$.data[0].endsAt").value("2026-11-23T09:00:00"))
                .andExpect(jsonPath("$.data[1].status").value("WAITING"))
                .andExpect(jsonPath("$.data[1].listed").value(false))
                .andExpect(jsonPath("$.data[1].playerCount").value(2))
                .andExpect(jsonPath("$.data[1].participantCount").value(1))
                .andExpect(jsonPath("$.data[1].waitingRoomExpiresAt")
                        .value("2026-11-23T15:00:00"))
                .andExpect(jsonPath("$.data[1].startedAt").isEmpty())
                .andExpect(jsonPath("$.data[1].tradingDay").isEmpty());
    }

    @Test
    @DisplayName("입장한 리그가 없으면 빈 배열로 응답한다")
    void getMyLeaguesEmpty() throws Exception {
        given(leagueQueryService.getMyLeagues(MEMBER_ID)).willReturn(List.of());

        mockMvc.perform(get("/api/leagues/me").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("로그인하지 않으면 리그 목록을 조회할 수 없다")
    void getMyLeaguesWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/leagues/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(leagueQueryService).should(never()).getMyLeagues(anyLong());
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
