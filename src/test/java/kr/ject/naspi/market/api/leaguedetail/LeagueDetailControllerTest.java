package kr.ject.naspi.market.api.leaguedetail;

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
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.application.LeagueDetail;
import kr.ject.naspi.market.application.LeagueDetail.DetailPlayer;
import kr.ject.naspi.market.application.LeagueDetail.PlayerStock;
import kr.ject.naspi.market.application.LeagueDetailService;
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
@WebMvcTest(LeagueDetailController.class)
@Import(SecurityConfig.class)
class LeagueDetailControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 21, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueDetailService leagueDetailService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("리그 플레이어는 플레이어 목록과 각 참여자의 종목, 리그 시작 가능 여부를 조회할 수 있다")
    void getDetail() throws Exception {
        given(leagueDetailService.getDetail(MEMBER_ID, 3L)).willReturn(new LeagueDetail(3L, false,
                List.of(new DetailPlayer(9L, "방장", true, true, NOW, NOW,
                                new PlayerStock(11L, "나스피", "소개")),
                        new DetailPlayer(10L, "손님", false, false, NOW, null, null))));

        mockMvc.perform(get("/api/leagues/3/detail").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.startable").value(false))
                .andExpect(jsonPath("$.data.players[0].host").value(true))
                .andExpect(jsonPath("$.data.players[0].listed").value(true))
                .andExpect(jsonPath("$.data.players[0].stock.name").value("나스피"))
                .andExpect(jsonPath("$.data.players[1].name").value("손님"))
                .andExpect(jsonPath("$.data.players[1].listed").value(false))
                .andExpect(jsonPath("$.data.players[1].stock").isEmpty());
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원이면 권한 없음 코드로 응답한다")
    void getDetailByOutsider() throws Exception {
        given(leagueDetailService.getDetail(MEMBER_ID, 3L))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/leagues/3/detail").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("004"));
    }

    @Test
    @DisplayName("로그인하지 않으면 리그 상세를 조회할 수 없다")
    void getDetailWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/leagues/3/detail"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(leagueDetailService).should(never()).getDetail(anyLong(), anyLong());
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
