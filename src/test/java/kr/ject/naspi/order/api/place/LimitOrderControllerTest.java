package kr.ject.naspi.order.api.place;

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
import kr.ject.naspi.order.application.OrderPlacementService;
import kr.ject.naspi.order.application.PlacedOrder;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
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
@WebMvcTest(LimitOrderController.class)
@Import(SecurityConfig.class)
class LimitOrderControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final long LEAGUE_ID = 3L;
    private static final String URL = "/api/leagues/3/orders/limit";
    private static final String BODY =
            "{\"stockId\": 5, \"side\": \"BUY\", \"price\": 10000, \"quantity\": 10}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPlacementService orderPlacementService;

    @MockitoBean
    private AuthenticationSuccessHandler kakaoLoginSuccessHandler;

    @Test
    @DisplayName("로그인한 회원은 지정가 주문을 넣고 접수 직후 상태를 받는다")
    void placeLimitOrder() throws Exception {
        given(orderPlacementService.placeLimitOrder(
                MEMBER_ID, LEAGUE_ID, 5L, OrderSide.BUY, 10_000L, 10L))
                .willReturn(new PlacedOrder(11L, OrderStatus.PENDING, 10L, 10L));

        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("000"))
                .andExpect(jsonPath("$.data.orderId").value(11))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.quantity").value(10))
                .andExpect(jsonPath("$.data.remainingQuantity").value(10));
    }

    @Test
    @DisplayName("주문하는 플레이어는 요청 본문이 아니라 로그인 회원으로 정한다")
    void ignoresPlayerIdInBody() throws Exception {
        given(orderPlacementService.placeLimitOrder(
                MEMBER_ID, LEAGUE_ID, 5L, OrderSide.BUY, 10_000L, 10L))
                .willReturn(new PlacedOrder(11L, OrderStatus.FILLED, 10L, 0L));

        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": 99, \"memberId\": 99, \"stockId\": 5, "
                                + "\"side\": \"BUY\", \"price\": 10000, \"quantity\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FILLED"));
    }

    @Test
    @DisplayName("매매 구분이 없으면 잘못된 요청으로 응답하고 주문하지 않는다")
    void placeWithoutSide() throws Exception {
        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\": 5, \"price\": 10000, \"quantity\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("001"));

        then(orderPlacementService).should(never())
                .placeLimitOrder(anyLong(), anyLong(), anyLong(), any(),
                        anyLong(), anyLong());
    }

    @Test
    @DisplayName("상·하한가를 벗어난 가격은 비즈니스 오류 코드로 응답한다")
    void placeOutOfLimit() throws Exception {
        given(orderPlacementService.placeLimitOrder(
                anyLong(), anyLong(), anyLong(), any(),
                anyLong(), anyLong()))
                .willThrow(new NaspiBizException(ErrorCode.ORDER_PRICE_OUT_OF_LIMIT));

        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("503"));
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 주문할 수 없다")
    void placeWithoutJoining() throws Exception {
        given(orderPlacementService.placeLimitOrder(
                anyLong(), anyLong(), anyLong(), any(),
                anyLong(), anyLong()))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post(URL).with(loginMember())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("004"));
    }

    @Test
    @DisplayName("로그인하지 않으면 주문할 수 없다")
    void placeWithoutLogin() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("003"));
    }

    private RequestPostProcessor loginMember() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(MEMBER_ID), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
