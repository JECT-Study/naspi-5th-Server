package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.application.StockEditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기실 내 종목 수정.
 */
@Tag(name = "종목", description = "종목 상장·수정")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks/{stockId}")
@RequiredArgsConstructor
public class StockEditController {

    private final StockEditService stockEditService;

    /**
     * 대기실에서 자신의 종목 이름과 소개를 수정한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param request 종목 수정 요청
     * @return 수정된 종목
     * @throws NaspiBizException 이름·소개가 규칙에 맞지 않거나, 종목이 없거나, 본인의 종목이 아니거나,
     *         이름이 같은 리그에서 중복되는 경우
     */
    @Operation(
            summary = "대기실 종목 수정 (이름·소개)",
            description = """
                    대기실에서 자신의 종목 이름과 소개를 수정한다. \
                    리그가 시작된 뒤에는 이름을 바꿀 수 없고 \
                    소개만 수정할 수 있다 (진행 중 종목 소개 수정 API).

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 본인이 상장한 종목만 수정할 수 있다.
                    - 종목 이름: 앞뒤 공백 제거 후 1~12자, 글자·숫자·공백만 가능. \
                    같은 리그 안에서 중복 불가 (현재 이름 그대로 보내는 것은 가능).
                    - 종목 소개: 1~50자, 모든 문자 가능.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 수정된 종목 \
                    (`stockId`, `name`, `description`).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"300"` | 종목 이름이 규칙에 맞지 않음 (누락 포함) | 메시지 표시 후 다시 입력 |
                    | 200 | `"301"` | 종목 소개가 비었거나 50자 초과 (누락 포함) | 메시지 표시 후 다시 입력 |
                    | 200 | `"302"` | 같은 리그에 같은 이름의 종목이 있음 | 다른 이름 입력 |
                    | 200 | `"304"` | 해당 리그에 종목이 없음 | 안내 후 이전 화면으로 |
                    | 200 | `"305"` | 본인의 종목이 아님 | 안내 후 이전 화면으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/edit")
    public Result<StockResponse> edit(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @RequestBody EditStockRequest request) {
        return Result.success(StockResponse.from(stockEditService.editInLobby(
                leagueId, loginMember.memberId(), stockId, request.name(), request.description())));
    }

    /**
     * 진행 중인 리그에서 자신의 종목 소개를 수정한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param request 종목 소개 수정 요청
     * @return 수정된 종목
     * @throws NaspiBizException 소개가 규칙에 맞지 않거나, 종목이 없거나, 본인의 종목이 아닌 경우
     */
    @Operation(
            summary = "진행 중 종목 소개 수정",
            description = """
                    진행 중인 리그에서 자신의 종목 소개를 수정한다. 이름은 바꿀 수 없다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 본인이 상장한 종목만 수정할 수 있다.
                    - 종목 소개: 1~50자, 모든 문자 가능.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 수정된 종목 \
                    (`stockId`, `name`, `description`).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"301"` | 종목 소개가 비었거나 50자 초과 (누락 포함) | 메시지 표시 후 다시 입력 |
                    | 200 | `"304"` | 해당 리그에 종목이 없음 | 안내 후 이전 화면으로 |
                    | 200 | `"305"` | 본인의 종목이 아님 | 안내 후 이전 화면으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/description")
    public Result<StockResponse> changeDescription(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @RequestBody ChangeStockDescriptionRequest request) {
        return Result.success(StockResponse.from(stockEditService.changeDescription(
                leagueId, loginMember.memberId(), stockId, request.description())));
    }
}
