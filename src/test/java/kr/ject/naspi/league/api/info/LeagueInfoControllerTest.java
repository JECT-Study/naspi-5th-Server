package kr.ject.naspi.league.api.info;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueSummary;
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
@WebMvcTest(LeagueInfoController.class)
@Import(SecurityConfig.class)
class LeagueInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 초대 코드로 리그 이름, 상태, 입장 인원, 참여자 수를 조회할 수 있다")
    void getInfo() throws Exception {
        given(leagueQueryService.getSummary("A1B2C3D"))
                .willReturn(new LeagueSummary(3L, "우리 리그", "WAITING", 4, 2));

        mockMvc.perform(get("/api/leagues/invite-codes/A1B2C3D").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.name").value("우리 리그"))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.playerCount").value(4))
                .andExpect(jsonPath("$.data.participantCount").value(2));
    }

    @Test
    @DisplayName("유효하지 않은 초대 코드면 초대 코드 오류 코드로 응답한다")
    void getInfoOfUnknownLeague() throws Exception {
        given(leagueQueryService.getSummary("A1B2C3D"))
                .willThrow(new NaspiBizException(ErrorCode.INVITE_CODE_NOT_FOUND));

        mockMvc.perform(get("/api/leagues/invite-codes/A1B2C3D").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("201"));
    }

    @Test
    @DisplayName("로그인하지 않으면 리그 정보를 조회할 수 없다")
    void getInfoWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/leagues/invite-codes/A1B2C3D"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(leagueQueryService).should(never()).getSummary(anyString());
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(7L), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
