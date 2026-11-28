package kr.ject.naspi.order.api.detail;

import static org.mockito.BDDMockito.given;
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
import kr.ject.naspi.order.application.OrderDetail;
import kr.ject.naspi.order.application.OrderQueryService;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.OrderFill;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@WebMvcTest(OrderDetailController.class)
@Import(SecurityConfig.class)
class OrderDetailControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final long LEAGUE_ID = 3L;
    private static final long ORDER_ID = 11L;
    private static final String URL = "/api/leagues/3/orders/11";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 15, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("지정가 주문 상세는 접수·체결·대기 내역을 코드 문자열로 응답하고 취소 내역은 null이다")
    void limitOrder() throws Exception {
        given(orderQueryService.getOrderDetail(MEMBER_ID, LEAGUE_ID, ORDER_ID))
                .willReturn(new OrderDetail(ORDER_ID, 5L, "종목", OrderSide.SELL, OrderType.LIMIT,
                        AT, 10, 11_000L, List.of(new OrderFill(AT, 11_000, 3, 33_000)), null, 7L));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.orderId").value(11))
                .andExpect(jsonPath("$.data.stockId").value(5))
                .andExpect(jsonPath("$.data.stockName").value("종목"))
                .andExpect(jsonPath("$.data.side").value("SELL"))
                .andExpect(jsonPath("$.data.type").value("LIMIT"))
                .andExpect(jsonPath("$.data.quantity").value(10))
                .andExpect(jsonPath("$.data.price").value(11_000))
                .andExpect(jsonPath("$.data.fills[0].price").value(11_000))
                .andExpect(jsonPath("$.data.fills[0].quantity").value(3))
                .andExpect(jsonPath("$.data.fills[0].amount").value(33_000))
                .andExpect(jsonPath("$.data.fills[0].executedAt").exists())
                .andExpect(jsonPath("$.data.fills[0].buyOrderId").doesNotExist())
                .andExpect(jsonPath("$.data.fills[0].sellOrderId").doesNotExist())
                .andExpect(jsonPath("$.data.fills[0].playerId").doesNotExist())
                .andExpect(jsonPath("$.data.cancellation").value((Object) null))
                .andExpect(jsonPath("$.data.pendingQuantity").value(7));
    }

    @Test
    @DisplayName("시장가 주문은 주문 가격이 null이고 취소 사유를 코드와 표시 문구로, 대기 수량은 null로 응답한다")
    void marketOrder() throws Exception {
        given(orderQueryService.getOrderDetail(MEMBER_ID, LEAGUE_ID, ORDER_ID))
                .willReturn(new OrderDetail(ORDER_ID, 5L, "종목", OrderSide.BUY, OrderType.MARKET,
                        AT, 10, null, List.of(),
                        new OrderDetail.Cancellation(10, AT, CancelReason.MARKET_NOT_FILLED),
                        null));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MARKET"))
                .andExpect(jsonPath("$.data.price").value((Object) null))
                .andExpect(jsonPath("$.data.fills").isEmpty())
                .andExpect(jsonPath("$.data.cancellation.quantity").value(10))
                .andExpect(jsonPath("$.data.cancellation.reason").value("MARKET_NOT_FILLED"))
                .andExpect(jsonPath("$.data.cancellation.reasonMessage").value("체결 가능 수량 없음"))
                .andExpect(jsonPath("$.data.cancellation.canceledAt").exists())
                .andExpect(jsonPath("$.data.pendingQuantity").value((Object) null));
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class,
            names = {"FORBIDDEN", "LEAGUE_NOT_FOUND", "LEAGUE_NOT_TRADABLE",
                    "LEAGUE_ALREADY_ENDED", "ORDER_NOT_FOUND"})
    @DisplayName("조회가 거절되면 사유별 에러 코드로 응답한다")
    void rejected(ErrorCode errorCode) throws Exception {
        given(orderQueryService.getOrderDetail(MEMBER_ID, LEAGUE_ID, ORDER_ID))
                .willThrow(new NaspiBizException(errorCode));

        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(errorCode.getCode()));
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
