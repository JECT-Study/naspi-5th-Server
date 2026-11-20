package kr.ject.naspi.order.api.cancel;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.SecurityConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.order.application.CanceledOrder;
import kr.ject.naspi.order.application.OrderPlacementService;
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
@WebMvcTest(OrderCancelController.class)
@Import(SecurityConfig.class)
class OrderCancelControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final long LEAGUE_ID = 3L;
    private static final long ORDER_ID = 11L;
    private static final String URL = "/api/leagues/3/orders/11/cancel";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPlacementService orderPlacementService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 본인 미체결 주문을 취소하고 체결 수량과 취소 수량을 받는다")
    void cancelOrder() throws Exception {
        given(orderPlacementService.cancelOrder(MEMBER_ID, LEAGUE_ID, ORDER_ID))
                .willReturn(new CanceledOrder(ORDER_ID, 10L, 3L, 7L));

        mockMvc.perform(post(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.orderId").value(11))
                .andExpect(jsonPath("$.data.quantity").value(10))
                .andExpect(jsonPath("$.data.filledQuantity").value(3))
                .andExpect(jsonPath("$.data.canceledQuantity").value(7));
    }

    @Test
    @DisplayName("본인 주문이 아니거나 없는 주문은 주문 없음 코드로 응답한다")
    void cancelUnknownOrder() throws Exception {
        given(orderPlacementService.cancelOrder(MEMBER_ID, LEAGUE_ID, ORDER_ID))
                .willThrow(new NaspiBizException(ErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(post(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("504"));
    }

    @Test
    @DisplayName("이미 체결 완료·취소·만료된 주문은 상태 오류 코드로 응답한다")
    void cancelClosedOrder() throws Exception {
        given(orderPlacementService.cancelOrder(MEMBER_ID, LEAGUE_ID, ORDER_ID))
                .willThrow(new NaspiBizException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION));

        mockMvc.perform(post(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("502"));
    }

    @Test
    @DisplayName("로그인하지 않으면 취소할 수 없다")
    void cancelWithoutLogin() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
