package kr.ject.naspi.league.api.creation;

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
import kr.ject.naspi.league.application.CreatedLeague;
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
@WebMvcTest(LeagueCreationController.class)
@Import(SecurityConfig.class)
class LeagueCreationControllerTest {

    private static final long MEMBER_ID = 7L;

    private static final String BODY = """
            {"leagueName": "우리끼리 주식왕", "stockName": "나스피", "stockDescription": "소개"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueService leagueService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 리그 이름과 자신의 종목으로 리그를 만들고 초대 코드를 받는다")
    void create() throws Exception {
        given(leagueService.create(MEMBER_ID, "우리끼리 주식왕", "나스피", "소개"))
                .willReturn(new CreatedLeague(3L, 9L, "A1B2C3D"));

        mockMvc.perform(post("/api/leagues")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.leagueId").value(3))
                .andExpect(jsonPath("$.data.inviteCode").value("A1B2C3D"));
    }

    @Test
    @DisplayName("리그 이름이 규칙에 맞지 않으면 리그 이름 오류 코드로 응답한다")
    void createWithInvalidLeagueName() throws Exception {
        given(leagueService.create(MEMBER_ID, "우리끼리 주식왕", "나스피", "소개"))
                .willThrow(new NaspiBizException(ErrorCode.INVALID_LEAGUE_NAME));

        mockMvc.perform(post("/api/leagues")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }

    @Test
    @DisplayName("필드가 빠지면 리그를 생성할 수 없다")
    void createWithMissingField() throws Exception {
        mockMvc.perform(post("/api/leagues")
                        .with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leagueName\": \"리그\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("002"));

        then(leagueService).should(never())
                .create(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("로그인하지 않으면 리그를 생성할 수 없다")
    void createWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/leagues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
