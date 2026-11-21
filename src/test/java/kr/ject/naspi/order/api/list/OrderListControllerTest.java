package kr.ject.naspi.order.api.list;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
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
import kr.ject.naspi.order.application.OrderListType;
import kr.ject.naspi.order.application.OrderQueryService;
import kr.ject.naspi.order.application.OrderSummary;
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
@WebMvcTest(OrderListController.class)
@Import(SecurityConfig.class)
class OrderListControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final long LEAGUE_ID = 3L;
    private static final String URL = "/api/leagues/3/orders";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 15, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("status=pending은 대기 목록으로 번역하고 0인 수량은 null로 응답한다")
    void pending() throws Exception {
        given(orderQueryService.getOrders(MEMBER_ID, LEAGUE_ID, OrderListType.PENDING, null))
                .willReturn(List.of(new OrderSummary(11L, 5L, "종목", OrderSide.SELL,
                        OrderType.LIMIT, AT, 10, 11_000L, 3L, 7L, null, 11_000L, false)));

        mockMvc.perform(get(URL).param("status", "pending").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.orders[0].orderId").value(11))
                .andExpect(jsonPath("$.data.orders[0].stockName").value("종목"))
                .andExpect(jsonPath("$.data.orders[0].side").value("SELL"))
                .andExpect(jsonPath("$.data.orders[0].type").value("LIMIT"))
                .andExpect(jsonPath("$.data.orders[0].quantity").value(10))
                .andExpect(jsonPath("$.data.orders[0].price").value(11_000))
                .andExpect(jsonPath("$.data.orders[0].filledQuantity").value(3))
                .andExpect(jsonPath("$.data.orders[0].pendingQuantity").value(7))
                .andExpect(jsonPath("$.data.orders[0].canceledQuantity").value((Object) null))
                .andExpect(jsonPath("$.data.orders[0].executionPrice").value(11_000))
                .andExpect(jsonPath("$.data.orders[0].averagedPrice").value(false));
    }

    @Test
    @DisplayName("status=completed와 stockId는 완료 목록과 종목 필터로 전달한다")
    void completedWithStock() throws Exception {
        given(orderQueryService.getOrders(MEMBER_ID, LEAGUE_ID, OrderListType.COMPLETED, 5L))
                .willReturn(List.of());

        mockMvc.perform(get(URL).param("status", "completed").param("stockId", "5")
                        .with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.orders").isEmpty());
    }

    @Test
    @DisplayName("status가 pending·completed(소문자)가 아니면 잘못된 요청 코드로 거절한다")
    void invalidStatus() throws Exception {
        for (String value : List.of("PENDING", "all", "")) {
            mockMvc.perform(get(URL).param("status", value).with(loginMember()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("001"));
        }
        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("status를 생략하면 거절한다")
    void missingStatus() throws Exception {
        mockMvc.perform(get(URL).with(loginMember()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("002"));
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class,
            names = {"FORBIDDEN", "LEAGUE_NOT_FOUND", "LEAGUE_NOT_TRADABLE",
                    "LEAGUE_ALREADY_ENDED", "STOCK_NOT_FOUND"})
    @DisplayName("조회가 거절되면 사유별 에러 코드로 응답한다")
    void rejected(ErrorCode errorCode) throws Exception {
        given(orderQueryService.getOrders(MEMBER_ID, LEAGUE_ID, OrderListType.PENDING, null))
                .willThrow(new NaspiBizException(errorCode));

        mockMvc.perform(get(URL).param("status", "pending").with(loginMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(errorCode.getCode()));
    }

    @Test
    @DisplayName("로그인하지 않으면 조회할 수 없다")
    void withoutLogin() throws Exception {
        mockMvc.perform(get(URL).param("status", "pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
