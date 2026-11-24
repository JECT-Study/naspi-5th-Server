package kr.ject.naspi.order.api.orderbook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.order.application.OrderBookQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 호가 조회 (ORD-12).
 */
@Tag(name = "주문")
@RestController
@RequiredArgsConstructor
class OrderBookController {

    private final OrderBookQueryService orderBookQueryService;

    /**
     * 로그인 회원이 진행 중인 리그의 종목 호가를 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @return 현재가와 매도·매수 호가
     * @throws NaspiBizException 조회가 거절되는 경우
     */
    @Operation(
            summary = "호가 조회",
            description = """
                    진행 중인 리그의 참여자가 리그 내 종목의 호가를 조회한다. \
                    대기 중인 지정가 주문의 미체결 잔여 수량을 가격별로 합산하며 주문자는 구분하지 않는다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`는 다음 값.
                    - `currentPrice`: 종목 현재가(원). 첫 체결 전에는 시초가
                    - `asks`: 매도 호가. 낮은 가격부터 최대 5단계. 주문이 없으면 빈 배열
                    - `bids`: 매수 호가. 높은 가격부터 최대 5단계. 주문이 없으면 빈 배열
                    - 각 단계는 `orderPrice`(주문 가격), `remainingQuantity`(미체결 잔여 수량 합계)

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"204"` | 리그를 찾을 수 없음 (만료·삭제된 대기실 포함) | 메시지 표시 |
                    | 200 | `"211"` | 아직 시작하지 않은 리그 | 메시지 표시 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 메시지 표시 |
                    | 200 | `"304"` | 리그에 없는 `stockId` | 메시지 표시 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/api/leagues/{leagueId}/stocks/{stockId}/orderbook")
    public Result<OrderBookResponse> getOrderBook(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId) {
        return Result.success(OrderBookResponse.from(orderBookQueryService.getOrderBook(
                loginMember.memberId(), leagueId, stockId)));
    }
}
