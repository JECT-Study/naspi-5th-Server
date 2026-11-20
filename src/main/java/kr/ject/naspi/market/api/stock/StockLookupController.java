package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.application.LeagueStockLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리그 종목 이름·소개 조회.
 */
@Tag(name = "종목", description = "종목 상장·수정·조회")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks")
@RequiredArgsConstructor
public class StockLookupController {

    private final LeagueStockLookupService leagueStockLookupService;

    /**
     * 리그에 상장된 종목의 이름과 소개를 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @return 종목 목록
     * @throws NaspiBizException 리그에 입장하지 않은 회원인 경우
     */
    @Operation(
            summary = "리그 종목 목록 조회 (이름·소개)",
            description = """
                    리그에 상장된 종목의 이름과 소개를 종목 ID 순으로 조회한다. \
                    시세(현재가 등)는 포함하지 않는다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 해당 리그에 입장한 회원(대기자 포함)만 조회할 수 있다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 종목 배열 \
                    (`stockId`, `name`, `description`, `listerName`, `mine`). \
                    `mine`이 `true`인 항목이 로그인한 회원 본인의 종목이다 \
                    ("내 종목"/"친구 종목" 구분, 소개 수정 아이콘 노출에 쓴다). \
                    `listerName`은 상장자 이름이고, 시작된 리그에서 탈퇴한 상장자면 `"탈퇴한 사용자"`다. 종목 이름·소개는 탈퇴 후에도 그대로다. \
                    상장된 종목이 없으면 빈 배열.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"204"` | 리그가 없음 (만료된 대기실 포함) | 메시지 표시 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping
    public Result<List<MemberStockResponse>> findAll(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId) {
        return Result.success(leagueStockLookupService.findAll(loginMember.memberId(), leagueId)
                .stream()
                .map(MemberStockResponse::from)
                .toList());
    }

    /**
     * 리그에 상장된 종목 하나의 이름과 소개를 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @return 종목
     * @throws NaspiBizException 리그에 입장하지 않은 회원이거나, 리그에 해당 종목이 없는 경우
     */
    @Operation(
            summary = "리그 종목 개별 조회 (이름·소개)",
            description = """
                    리그에 상장된 종목 하나의 이름과 소개를 조회한다. 시세는 포함하지 않는다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 해당 리그에 입장한 회원(대기자 포함)만 조회할 수 있다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 종목 \
                    (`stockId`, `name`, `description`, `listerName`, `mine`). \
                    `mine`이 `true`이면 로그인한 회원 본인의 종목이다 (소개 수정 아이콘 노출에 쓴다). \
                    `listerName`은 상장자 이름이고, 시작된 리그에서 탈퇴한 상장자면 `"탈퇴한 사용자"`다. 종목 이름·소개는 탈퇴 후에도 그대로다.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"204"` | 리그가 없음 (만료된 대기실 포함) | 메시지 표시 |
                    | 200 | `"304"` | 해당 리그에 종목이 없음 | 안내 후 이전 화면으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/{stockId}")
    public Result<MemberStockResponse> findOne(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId) {
        return Result.success(MemberStockResponse.from(
                leagueStockLookupService.findOne(loginMember.memberId(), leagueId, stockId)));
    }
}
