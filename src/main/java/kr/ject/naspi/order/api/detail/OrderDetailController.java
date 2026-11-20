package kr.ject.naspi.order.api.detail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.order.application.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 상세 조회 (ORD-08).
 */
@Tag(name = "주문")
@RestController
@RequiredArgsConstructor
class OrderDetailController {

    private final OrderQueryService orderQueryService;

    /**
     * 로그인 회원이 본인 주문 1건의 접수·체결·취소·대기 내역을 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param orderId 주문 ID
     * @return 주문 상세
     * @throws NaspiBizException 조회가 거절되는 경우
     */
    @Operation(
            summary = "주문 상세 조회",
            description = """
                    로그인 회원이 진행 중인 리그에서 본인이 접수한 주문 1건의 접수·체결·취소·대기 내역을 조회한다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 요청 본문은 없다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 다음 값.
                    - `orderId`, `stockId`, `stockName`: 주문·종목 정보
                    - `side`: `BUY`(매수) / `SELL`(매도)
                    - `type`: `LIMIT`(지정가) / `MARKET`(시장가)
                    - `orderedAt`: 접수 일시
                    - `quantity`: 최초 주문 수량
                    - `price`: 지정가 주문 가격. 시장가는 `null`
                    - `fills`: 체결 내역 (체결 일시 오름차순). 체결 일시와 단가가 모두 같은 체결은 \
                    수량을 합산해 1건으로 제공한다. 각 건은 `executedAt`, `price`, `quantity`, `amount`. \
                    체결이 없으면 빈 배열
                    - `cancellation`: 취소 내역 `quantity`, `canceledAt`, `reason`. 취소 수량이 없으면 `null`. \
                    거래일 변경으로 만료된 수량도 취소 수량에 포함한다
                    - `pendingQuantity`: 현재 대기 수량 (최초 수량 - 체결 수량 - 취소 수량). 없으면 `null`

                    **취소 사유 `reason`** (표시 문구는 클라이언트가 매핑)

                    | 코드 | 표시 문구 |
                    |---|---|
                    | `USER_REQUESTED` | 직접 취소 |
                    | `MARKET_PARTIALLY_FILLED` | 체결 가능 수량 부족 |
                    | `MARKET_NOT_FILLED` | 체결 가능 수량 없음 |
                    | `SELF_TRADE_PREVENTED` | 내 주문 간 체결 방지 |
                    | `TRADING_DAY_EXPIRED` | 거래일 변경으로 만료 |

                    `LEAGUE_ENDED`(리그 종료), `MEMBER_WITHDRAWN`(회원 탈퇴) 사유는 ORD-10/11에서 추가한다.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"204"` | 리그를 찾을 수 없음 (만료·삭제된 대기실 포함) | 메시지 표시 |
                    | 200 | `"211"` | 아직 시작하지 않은 리그 | 메시지 표시 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 메시지 표시 |
                    | 200 | `"504"` | 주문이 없거나 본인 주문이 아님 | 주문 목록 새로고침 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/api/leagues/{leagueId}/orders/{orderId}")
    public Result<OrderDetailResponse> getOrderDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long orderId) {
        return Result.success(OrderDetailResponse.from(
                orderQueryService.getOrderDetail(loginMember.memberId(), leagueId, orderId)));
    }
}
