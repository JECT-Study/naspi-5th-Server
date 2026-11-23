package kr.ject.naspi.order.api.place;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.order.application.OrderPlacementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지정가 주문 접수 (ORD-02).
 */
@Tag(name = "주문", description = "지정가 주문 접수")
@RestController
@RequiredArgsConstructor
class LimitOrderController {

    private final OrderPlacementService orderPlacementService;

    /**
     * 로그인 회원이 리그의 종목에 지정가 주문을 넣는다. 접수 직후 매칭되어 체결될 수 있다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param request 주문 요청
     * @return 접수 직후 주문 상태
     * @throws NaspiBizException 요청이 잘못되었거나 접수가 거절되는 경우
     */
    @Operation(
            summary = "지정가 주문",
            description = """
                    로그인 회원이 리그의 종목에 가격과 수량을 지정해 매수·매도 주문을 넣는다. \
                    접수 직후 상대 주문과 매칭되어 체결되고, 체결되지 않은 잔여 수량은 대기 주문으로 남는다. \
                    주문하는 플레이어는 로그인 회원 본인으로 서버가 정한다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - `stockId`: 종목 ID, `side`: `BUY` 또는 `SELL`
                    - `price`: 1주당 가격, 100원 이상 100원 단위이며 당일 상·하한가 범위 안
                    - `quantity`: 1주 이상

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 다음 값.
                    - `orderId`: 주문 ID
                    - `status`: `PENDING`(대기), `PARTIALLY_FILLED`(부분 체결), \
                    `FILLED`(체결 완료), `CANCELED`(자전거래로 취소)
                    - `quantity`: 주문 수량, `remainingQuantity`: 미체결 잔여 수량 \
                    (자전거래로 취소된 경우 취소된 수량)

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"001"` | 매매 구분 누락 | 입력 확인 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"204"` | 리그를 찾을 수 없음 (만료·삭제된 대기실 포함) | 메시지 표시 |
                    | 200 | `"211"` | 아직 시작하지 않은 리그 | 메시지 표시 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 메시지 표시 |
                    | 200 | `"213"` | 09:00 거래일 전환 처리 전 | 잠시 후 재시도 |
                    | 200 | `"304"` | 리그에 없는 종목 | 메시지 표시 |
                    | 200 | `"401"` | 주문 가능한 현금 부족 (매수) | 메시지 표시 |
                    | 200 | `"402"` | 매도 가능한 수량 부족 (매도) | 메시지 표시 |
                    | 200 | `"500"` | 가격이 100원 이상 100원 단위가 아님 | 다시 입력 |
                    | 200 | `"501"` | 수량이 1주 미만 | 다시 입력 |
                    | 200 | `"503"` | 가격이 당일 상·하한가를 벗어남 | 다시 입력 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/api/leagues/{leagueId}/orders/limit")
    public Result<PlaceLimitOrderResponse> placeLimitOrder(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @RequestBody PlaceLimitOrderRequest request) {
        if (request.side() == null) {
            throw new NaspiBizException(ErrorCode.INVALID_REQUEST);
        }
        return Result.success(PlaceLimitOrderResponse.from(orderPlacementService.placeLimitOrder(
                loginMember.memberId(), leagueId, request.stockId(), request.side(),
                request.price(), request.quantity())));
    }
}
