package kr.ject.naspi.order.api.cancel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.order.application.OrderPlacementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미체결 주문 취소 (ORD-07).
 */
@Tag(name = "주문")
@RestController
@RequiredArgsConstructor
class OrderCancelController {

    private final OrderPlacementService orderPlacementService;

    /**
     * 로그인 회원이 본인의 미체결 주문을 취소한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param orderId 주문 ID
     * @return 취소 결과
     * @throws NaspiBizException 취소가 거절되는 경우
     */
    @Operation(
            summary = "미체결 주문 취소",
            description = """
                    로그인 회원이 본인의 대기·부분 체결 주문에서 미체결 잔여 수량을 취소한다. \
                    잔여 수량에 묶인 현금(매수)이나 주식(매도) 예약이 해제되고, 이미 체결된 부분은 유지된다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 요청 본문은 없다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 다음 값.
                    - `orderId`: 주문 ID
                    - `quantity`: 주문 수량
                    - `filledQuantity`: 취소 전에 체결된 수량 (유지)
                    - `canceledQuantity`: 취소된 미체결 잔여 수량

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"204"` | 리그를 찾을 수 없음 (만료·삭제된 대기실 포함) | 메시지 표시 |
                    | 200 | `"211"` | 아직 시작하지 않은 리그 | 메시지 표시 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 메시지 표시 |
                    | 200 | `"502"` | 이미 체결 완료·취소·만료된 주문 | 주문 목록 새로고침 |
                    | 200 | `"504"` | 주문이 없거나 본인 주문이 아님 | 주문 목록 새로고침 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/api/leagues/{leagueId}/orders/{orderId}/cancel")
    public Result<CancelOrderResponse> cancelOrder(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long orderId) {
        return Result.success(CancelOrderResponse.from(
                orderPlacementService.cancelOrder(loginMember.memberId(), leagueId, orderId)));
    }
}
