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
import kr.ject.naspi.market.application.StockDetail;
import kr.ject.naspi.market.application.StockEditService;
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
@WebMvcTest(StockEditController.class)
@Import(SecurityConfig.class)
class StockEditControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String EDIT_URL = "/api/leagues/3/stocks/11/edit";
    private static final String DESCRIPTION_URL = "/api/leagues/3/stocks/11/description";
    private static final String EDIT_BODY = "{\"name\": \"철수전자\", \"description\": \"성실함\"}";
    private static final String DESCRIPTION_BODY = "{\"description\": \"성실함\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockEditService stockEditService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 대기실에서 자신의 종목 이름과 소개를 수정한다")
    void edit() throws Exception {
        given(stockEditService.editInLobby(3L, MEMBER_ID, 11L, "철수전자", "성실함"))
                .willReturn(new StockDetail(11L, "철수전자", "성실함"));

        mockMvc.perform(post(EDIT_URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.stockId").value(11))
                .andExpect(jsonPath("$.data.name").value("철수전자"))
                .andExpect(jsonPath("$.data.description").value("성실함"));
    }

    @Test
    @DisplayName("대기실 수정에서 이름이 중복되면 비즈니스 오류 코드로 응답한다")
    void editWithDuplicatedName() throws Exception {
        given(stockEditService.editInLobby(anyLong(), anyLong(), anyLong(), any(), any()))
                .willThrow(new NaspiBizException(ErrorCode.STOCK_NAME_DUPLICATED));

        mockMvc.perform(post(EDIT_URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("302"));
    }

    @Test
    @DisplayName("본인의 종목이 아니면 수정할 수 없다")
    void editNotOwned() throws Exception {
        given(stockEditService.editInLobby(anyLong(), anyLong(), anyLong(), any(), any()))
                .willThrow(new NaspiBizException(ErrorCode.STOCK_NOT_OWNED));

        mockMvc.perform(post(EDIT_URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("305"));
    }

    @Test
    @DisplayName("로그인하지 않으면 종목을 수정할 수 없다")
    void editWithoutLogin() throws Exception {
        mockMvc.perform(post(EDIT_URL)
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(stockEditService).should(never())
                .editInLobby(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("로그인한 회원은 자신의 종목 소개만 수정한다")
    void changeDescription() throws Exception {
        given(stockEditService.changeDescription(3L, MEMBER_ID, 11L, "성실함"))
                .willReturn(new StockDetail(11L, "철수전자", "성실함"));

        mockMvc.perform(post(DESCRIPTION_URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(DESCRIPTION_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.name").value("철수전자"))
                .andExpect(jsonPath("$.data.description").value("성실함"));
    }

    @Test
    @DisplayName("소개가 규칙에 맞지 않으면 비즈니스 오류 코드로 응답한다")
    void changeDescriptionWithInvalidDescription() throws Exception {
        given(stockEditService.changeDescription(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new NaspiBizException(ErrorCode.INVALID_STOCK_DESCRIPTION));

        mockMvc.perform(post(DESCRIPTION_URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(DESCRIPTION_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("301"));
    }

    @Test
    @DisplayName("로그인하지 않으면 종목 소개를 수정할 수 없다")
    void changeDescriptionWithoutLogin() throws Exception {
        mockMvc.perform(post(DESCRIPTION_URL)
                        .contentType(MediaType.APPLICATION_JSON).content(DESCRIPTION_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));

        then(stockEditService).should(never())
                .changeDescription(anyLong(), anyLong(), anyLong(), any());
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
