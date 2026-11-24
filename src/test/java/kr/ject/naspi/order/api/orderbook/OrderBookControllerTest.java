package kr.ject.naspi.order.api.orderbook;

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
import kr.ject.naspi.order.application.OrderBookQueryService;
import kr.ject.naspi.order.application.OrderBookView;
import kr.ject.naspi.order.application.OrderBookView.PriceLevel;
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
@WebMvcTest(OrderBookController.class)
@Import(SecurityConfig.class)
class OrderBookControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String URL = "/api/leagues/3/stocks/5/orderbook";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderBookQueryService orderBookQueryService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("호가 응답은 현재가와 매도·매수 호가의 가격·잔여 수량을 담는다")
    void getOrderBook() throws Exception {
        given(orderBookQueryService.getOrderBook(MEMBER_ID, 3L, 5L)).willReturn(
                new OrderBookView(10_000, List.of(new PriceLevel(10_100, 7)),
                        List.of(new PriceLevel(9_900, 4))));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.currentPrice").value(10_000))
                .andExpect(jsonPath("$.data.asks[0].orderPrice").value(10_100))
                .andExpect(jsonPath("$.data.asks[0].remainingQuantity").value(7))
                .andExpect(jsonPath("$.data.bids[0].orderPrice").value(9_900))
                .andExpect(jsonPath("$.data.bids[0].remainingQuantity").value(4));
    }

    @Test
    @DisplayName("주문이 없는 쪽은 빈 배열로 응답한다")
    void emptySide() throws Exception {
        given(orderBookQueryService.getOrderBook(MEMBER_ID, 3L, 5L)).willReturn(
                new OrderBookView(10_000, List.of(), List.of(new PriceLevel(9_900, 4))));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asks").isEmpty())
                .andExpect(jsonPath("$.data.bids[0].orderPrice").value(9_900));
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원의 조회는 거절한다")
    void forbidden() throws Exception {
        given(orderBookQueryService.getOrderBook(MEMBER_ID, 3L, 5L))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("로그인하지 않으면 조회할 수 없다")
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
