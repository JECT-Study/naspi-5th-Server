package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.application.StockListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 종목 상장.
 */
@Tag(name = "종목", description = "종목 상장")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks")
@RequiredArgsConstructor
public class StockListingController {

    private final StockListingService stockListingService;

    /**
     * 로그인한 회원이 리그에 자신의 종목을 상장한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param request 종목 상장 요청
     * @return 상장된 종목 ID
     * @throws NaspiBizException 이름·소개가 규칙에 맞지 않거나, 이름이 같은 리그에서 중복되거나,
     *         이미 상장한 경우
     */
    @Operation(
            summary = "종목 상장",
            description = """
                    로그인한 회원이 리그에 자신의 종목을 상장한다. 리그당 1인 1종목.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 종목 이름: 앞뒤 공백 제거 후 1~12자, 글자·숫자·공백만 가능 \
                    (특수문자·이모지 불가). 같은 리그 안에서 중복 불가.
                    - 종목 소개: 1~50자, 모든 문자 가능.

                    **성공 시** HTTP 200, `code` `"000"`, `data.stockId`에 상장된 종목 ID.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"300"` | 종목 이름이 규칙에 맞지 않음 (누락 포함) | 메시지 표시 후 다시 입력 |
                    | 200 | `"301"` | 종목 소개가 비었거나 50자 초과 (누락 포함) | 메시지 표시 후 다시 입력 |
                    | 200 | `"302"` | 같은 리그에 같은 이름의 종목이 있음 | 다른 이름 입력 |
                    | 200 | `"303"` | 이미 종목을 상장함 | 안내 후 대기실로 이동 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping
    public Result<ListStockResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @RequestBody ListStockRequest request) {
        long stockId = stockListingService.list(
                leagueId, loginMember.memberId(), request.name(), request.description());
        return Result.success(new ListStockResponse(stockId));
    }
}
