package kr.ject.naspi.market.api.stock;

import static org.mockito.ArgumentMatchers.any;
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
import kr.ject.naspi.market.application.StockListingService;
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
@WebMvcTest(StockListingController.class)
@Import(SecurityConfig.class)
class StockListingControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final long LEAGUE_ID = 3L;
    private static final String URL = "/api/leagues/3/stocks";
    private static final String BODY = "{\"name\": \"철수전자\", \"description\": \"성실함\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockListingService stockListingService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 리그에 종목을 상장하고 종목 ID를 받는다")
    void list() throws Exception {
        given(stockListingService.list(LEAGUE_ID, MEMBER_ID, "철수전자", "성실함")).willReturn(11L);

        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.stockId").value(11));
    }

    @Test
    @DisplayName("종목 이름이 규칙에 맞지 않으면 비즈니스 오류 코드로 응답한다")
    void listWithInvalidName() throws Exception {
        given(stockListingService.list(anyLong(), anyLong(), any(), any()))
                .willThrow(new NaspiBizException(ErrorCode.INVALID_STOCK_NAME));

        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("300"));
    }

    @Test
    @DisplayName("같은 리그에 같은 이름의 종목이 있으면 상장할 수 없다")
    void listWithDuplicatedName() throws Exception {
        given(stockListingService.list(anyLong(), anyLong(), any(), any()))
                .willThrow(new NaspiBizException(ErrorCode.STOCK_NAME_DUPLICATED));

        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("302"));
    }

    @Test
    @DisplayName("이미 종목을 상장했으면 다시 상장할 수 없다")
    void listAgain() throws Exception {
        given(stockListingService.list(anyLong(), anyLong(), any(), any()))
                .willThrow(new NaspiBizException(ErrorCode.STOCK_ALREADY_LISTED));

        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("303"));
    }

    @Test
    @DisplayName("로그인하지 않으면 종목을 상장할 수 없다")
    void listWithoutLogin() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(stockListingService).should(never()).list(anyLong(), anyLong(), any(), any());
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
