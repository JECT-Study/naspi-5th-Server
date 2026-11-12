package kr.ject.naspi.asset.api.me;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import kr.ject.naspi.asset.application.MyAssetQueryService;
import kr.ject.naspi.asset.application.PlayerAsset;
import kr.ject.naspi.asset.application.PlayerAsset.HoldingAsset;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
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
@WebMvcTest(MyAssetController.class)
@Import(SecurityConfig.class)
class MyAssetControllerTest {

    private static final long MEMBER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MyAssetQueryService myAssetQueryService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("참여자는 본인의 현금, 종목별 평가 금액·평가 손익·수익률, 총자산과 총자산 평가 손익·수익률을 조회할 수 있다")
    void getMyAsset() throws Exception {
        given(myAssetQueryService.getMyAsset(MEMBER_ID, 3L)).willReturn(new PlayerAsset(30L,
                50_000_000, 1_000_000, 49_000_000, 52_500_000, 102_500_000, 2_500_000,
                new BigDecimal("2.5"),
                List.of(new HoldingAsset(11L, "나스피", true, 2_500, 0, 1_500, 12_000,
                        30_000_000, 5_000_000, new BigDecimal("20.0")))));

        mockMvc.perform(get("/api/leagues/3/assets/me").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.playerId").value(30))
                .andExpect(jsonPath("$.data.deposit").value(50_000_000))
                .andExpect(jsonPath("$.data.orderableDeposit").value(49_000_000))
                .andExpect(jsonPath("$.data.totalAsset").value(102_500_000))
                .andExpect(jsonPath("$.data.totalProfit").value(2_500_000))
                .andExpect(jsonPath("$.data.totalReturnRate").value(2.5))
                .andExpect(jsonPath("$.data.holdings[0].stockName").value("나스피"))
                .andExpect(jsonPath("$.data.holdings[0].treasuryStock").value(true))
                .andExpect(jsonPath("$.data.holdings[0].quantity").value(2_500))
                .andExpect(jsonPath("$.data.holdings[0].valuation").value(30_000_000))
                .andExpect(jsonPath("$.data.holdings[0].profit").value(5_000_000))
                .andExpect(jsonPath("$.data.holdings[0].returnRate").value(20.0));
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원이면 권한 없음 코드로 응답한다")
    void getMyAssetByOutsider() throws Exception {
        given(myAssetQueryService.getMyAsset(MEMBER_ID, 3L))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/leagues/3/assets/me").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("004"));
    }

    @Test
    @DisplayName("로그인하지 않으면 자산을 조회할 수 없다")
    void getMyAssetWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/leagues/3/assets/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(myAssetQueryService).should(never()).getMyAsset(anyLong(), anyLong());
    }

    private static RequestPostProcessor loginMember() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
