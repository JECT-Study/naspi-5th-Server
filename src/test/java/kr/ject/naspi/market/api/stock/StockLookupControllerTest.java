package kr.ject.naspi.market.api.stock;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.application.LeagueStockLookupService;
import kr.ject.naspi.market.application.MemberStock;
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
@WebMvcTest(StockLookupController.class)
@Import(SecurityConfig.class)
class StockLookupControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/stocks";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeagueStockLookupService leagueStockLookupService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 리그 종목의 이름과 소개, 본인 종목 여부를 조회한다")
    void findAll() throws Exception {
        given(leagueStockLookupService.findAll(MEMBER_ID, 3L)).willReturn(List.of(
                new MemberStock(11L, "철수전자", "성실함", "철수", true),
                new MemberStock(12L, "영희물산", "꾸준함", null, false)));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].stockId").value(11))
                .andExpect(jsonPath("$.data[0].name").value("철수전자"))
                .andExpect(jsonPath("$.data[0].description").value("성실함"))
                .andExpect(jsonPath("$.data[0].listerName").value("철수"))
                .andExpect(jsonPath("$.data[0].mine").value(true))
                .andExpect(jsonPath("$.data[1].mine").value(false));
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원이면 권한 오류 코드로 응답한다")
    void forbidden() throws Exception {
        given(leagueStockLookupService.findAll(anyLong(), anyLong()))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("004"));
    }

    @Test
    @DisplayName("로그인한 회원은 종목 하나의 이름과 소개를 조회한다")
    void findOne() throws Exception {
        given(leagueStockLookupService.findOne(MEMBER_ID, 3L, 11L))
                .willReturn(new MemberStock(11L, "철수전자", "성실함", "철수", true));

        mockMvc.perform(get(URL + "/11").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.stockId").value(11))
                .andExpect(jsonPath("$.data.name").value("철수전자"))
                .andExpect(jsonPath("$.data.mine").value(true));
    }

    @Test
    @DisplayName("리그에 없는 종목이면 종목 없음 오류 코드로 응답한다")
    void findOneNotFound() throws Exception {
        given(leagueStockLookupService.findOne(anyLong(), anyLong(), anyLong()))
                .willThrow(new NaspiBizException(ErrorCode.STOCK_NOT_FOUND));

        mockMvc.perform(get(URL + "/99").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("304"));
    }

    @Test
    @DisplayName("로그인하지 않으면 종목을 조회할 수 없다")
    void withoutLogin() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
