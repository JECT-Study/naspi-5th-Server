package kr.ject.naspi.asset.api.me;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.asset.application.MyAssetQueryService;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 자산 조회 (AST-02). 화면 첫 진입·새로고침·재접속 시 한 번 조회하고, 이후 변동은 웹소켓 내 자산 토픽으로 받는다.
 */
@Tag(name = "자산", description = "리그 참여자 본인의 자산 조회")
@RestController
@RequiredArgsConstructor
class MyAssetController {

    private final MyAssetQueryService myAssetQueryService;

    /**
     * 로그인 회원 본인의 리그 자산을 조회한다. 경로로 플레이어 ID를 받지 않는다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @return 내 자산
     * @throws NaspiBizException 리그에 입장하지 않은 회원이거나 자산이 없는 경우
     */
    @Operation(
            summary = "내 자산 조회",
            description = """
                    리그에서 본인의 보유 현금, 보유 종목별 평가 금액·평가 손익·수익률, \
                    총자산과 총자산 평가 손익·수익률을 조회한다. 평가 가격은 현재가다. \
                    해당 리그에 입장한 회원 본인의 자산만 조회할 수 있다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 다음 값. 금액은 원, 수량은 주.
                    - `playerId`: 내 플레이어 ID. 웹소켓 내 자산 토픽 \
                    `/topic/leagues/{leagueId}/players/{playerId}/assets` 구독에 쓴다
                    - `deposit`: 보유 현금 (예약금 포함), `orderableDeposit`: 주문 가능 현금
                    - `stockValuation`: 보유 주식 평가 금액 합계
                    - `totalAsset`: 총자산, `totalProfit`: 총자산 평가 손익 (총자산 - 1억 원)
                    - `totalReturnRate`: 총자산 수익률(%), 소수점 첫째 자리까지 (예: `2.5`, `-12.4`)
                    - `holdings`: 보유 수량이 있는 종목, 종목 ID 순
                      - `stockName`, `treasuryStock`(내 종목 여부), `quantity`(보유 수량), \
                    `orderableQuantity`(매도 가능 수량), `currentPrice`
                      - `averagePrice`: 평균 매입가(평단가, 이동평균법), 소수점 둘째 자리까지 (예: `11000.00`)
                      - `valuation`(평가 금액), `profit`(평가 손익 = 평가 금액 - 매입 금액), \
                    `returnRate`(수익률 % = 평가 손익 ÷ 매입 금액 × 100)

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"400"` | 자산이 없음 (리그 시작 전) | 메시지 표시 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/api/leagues/{leagueId}/assets/me")
    public Result<MyAssetResponse> getMyAsset(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId) {
        return Result.success(MyAssetResponse.from(
                myAssetQueryService.getMyAsset(loginMember.memberId(), leagueId)));
    }
}
