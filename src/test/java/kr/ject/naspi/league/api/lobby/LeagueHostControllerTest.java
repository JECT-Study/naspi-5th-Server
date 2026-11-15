package kr.ject.naspi.league.api.lobby;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
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
import kr.ject.naspi.league.application.LeagueHostUseCase;
import kr.ject.naspi.league.application.RenamedLeague;
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
@WebMvcTest(LeagueHostController.class)
@Import(SecurityConfig.class)
class LeagueHostControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueHostUseCase leagueHostUseCase;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("방장은 대기실에서 리그 이름을 수정할 수 있다")
    void rename() throws Exception {
        given(leagueHostUseCase.rename(MEMBER_ID, 3L, "새 이름"))
                .willReturn(new RenamedLeague(3L, "새 이름"));

        mockMvc.perform(post("/api/leagues/3/name").with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.leagueId").value(3))
                .andExpect(jsonPath("$.data.name").value("새 이름"));
    }

    @Test
    @DisplayName("리그 이름이 없으면 수정할 수 없다")
    void renameWithoutName() throws Exception {
        mockMvc.perform(post("/api/leagues/3/name").with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("002"));

        then(leagueHostUseCase).should(never()).rename(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("방장이 아니면 리그 이름을 수정할 수 없다")
    void renameByNonHost() throws Exception {
        given(leagueHostUseCase.rename(MEMBER_ID, 3L, "이름"))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_HOST_ONLY));

        mockMvc.perform(post("/api/leagues/3/name").with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("207"));
    }

    @Test
    @DisplayName("방장은 대기실에서 리그를 삭제할 수 있다")
    void delete() throws Exception {
        mockMvc.perform(post("/api/leagues/3/delete").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"));

        then(leagueHostUseCase).should().delete(MEMBER_ID, 3L);
    }

    @Test
    @DisplayName("리그 시작 이후에는 리그를 삭제할 수 없다")
    void deleteAfterStart() throws Exception {
        willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_STARTED))
                .given(leagueHostUseCase).delete(MEMBER_ID, 3L);

        mockMvc.perform(post("/api/leagues/3/delete").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("208"));
    }

    @Test
    @DisplayName("방장은 대기실에서 다른 플레이어를 추방할 수 있다")
    void kick() throws Exception {
        mockMvc.perform(post("/api/leagues/3/players/11/kick").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"));

        then(leagueHostUseCase).should().kick(MEMBER_ID, 3L, 11L);
    }

    @Test
    @DisplayName("방장은 자기 자신을 추방할 수 없다")
    void kickSelf() throws Exception {
        willThrow(new NaspiBizException(ErrorCode.LEAGUE_HOST_NOT_KICKABLE))
                .given(leagueHostUseCase).kick(MEMBER_ID, 3L, 11L);

        mockMvc.perform(post("/api/leagues/3/players/11/kick").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("209"));
    }

    @Test
    @DisplayName("로그인하지 않으면 방장 기능을 쓸 수 없다")
    void withoutLogin() throws Exception {
        mockMvc.perform(post("/api/leagues/3/delete"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(leagueHostUseCase).should(never()).delete(anyLong(), anyLong());
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
