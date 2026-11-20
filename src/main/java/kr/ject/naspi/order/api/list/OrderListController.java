package kr.ject.naspi.order.api.list;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.order.application.OrderListType;
import kr.ject.naspi.order.application.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 목록 조회 (ORD-13).
 */
@Tag(name = "주문")
@RestController
@RequiredArgsConstructor
class OrderListController {

    private final OrderQueryService orderQueryService;

    /**
     * 로그인 회원이 본인의 대기 주문 또는 완료된 주문 목록을 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param status {@code pending} 또는 {@code completed}
     * @param stockId 종목 ID. 생략하면 전체 종목
     * @return 주문 목록
     * @throws NaspiBizException 조회가 거절되는 경우
     */
    @Operation(
            summary = "주문 목록 조회",
            description = """
                    로그인 회원이 진행 중인 리그에서 본인이 접수한 대기 주문 또는 완료된 주문 목록을 \
                    접수 역순(최신 먼저)으로 조회한다. 페이지네이션은 없다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - `status`(필수, 소문자): `pending`(대기 주문) / `completed`(완료된 주문). \
                    일부 체결 후 잔여 수량이 대기 중인 주문은 두 목록에 모두 나온다. \
                    전량 대기 중인 주문은 완료된 주문이 아니다.
                    - `stockId`(선택): 생략하면 전체 종목, 지정하면 해당 종목 주문만

                    **성공 시** HTTP 200, `code` `"000"`, `data.orders`에 주문 배열 (없으면 빈 배열). \
                    각 주문은 다음 값.
                    - `orderId`, `stockId`, `stockName`: 주문·종목 정보
                    - `side`: `BUY`(매수) / `SELL`(매도)
                    - `type`: `LIMIT`(지정가) / `MARKET`(시장가)
                    - `orderedAt`: 접수 일시
                    - `quantity`: 최초 주문 수량
                    - `price`: 지정가 주문 가격. 시장가는 `null`
                    - `filledQuantity`, `pendingQuantity`, `canceledQuantity`: 체결·대기·취소 수량. \
                    0주면 `null`. 거래일 변경으로 만료된 수량은 취소 수량에 포함한다
                    - `executionPrice`: 체결 단가. 체결 단가가 한 가지면 주당 체결 단가, 둘 이상이면 \
                    평균 체결 단가(총 체결 금액 ÷ 총 체결 수량, 원 미만 절사). 체결 수량이 0주면 `null`
                    - `averagedPrice`: `executionPrice`가 평균 체결 단가이면 `true`

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"001"` | `status`가 `pending`/`completed`가 아님 | 요청 값 수정 |
                    | 400 | `"002"` | `status` 누락, `stockId` 형식 오류 | 요청 값 수정 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"204"` | 리그를 찾을 수 없음 (만료·삭제된 대기실 포함) | 메시지 표시 |
                    | 200 | `"211"` | 아직 시작하지 않은 리그 | 메시지 표시 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 메시지 표시 |
                    | 200 | `"304"` | 리그에 없는 `stockId` | 메시지 표시 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/api/leagues/{leagueId}/orders")
    public Result<OrderListResponse> getOrders(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @RequestParam String status,
            @RequestParam(required = false) Long stockId) {
        return Result.success(OrderListResponse.from(orderQueryService.getOrders(
                loginMember.memberId(), leagueId, toType(status), stockId)));
    }

    private OrderListType toType(String status) {
        return switch (status) {
            case "pending" -> OrderListType.PENDING;
            case "completed" -> OrderListType.COMPLETED;
            default -> throw new NaspiBizException(ErrorCode.INVALID_REQUEST);
        };
    }
}
