package kr.ject.naspi.league.api.start;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueStartUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방장의 리그 시작.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장")
@RestController
@RequiredArgsConstructor
public class LeagueStartController {

    private final LeagueStartUseCase leagueStartUseCase;

    /**
     * 방장이 리그를 시작한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @return 시작한 리그 ID와 시작 일시
     * @throws NaspiBizException 시작 조건을 만족하지 않거나 방장이 아닌 경우 등
     */
    @Operation(
            summary = "리그 시작",
            description = """
                    방장이 대기실의 리그를 시작한다. 시작하면 모든 참여자에게 초기 자산이 배분되고 거래가 열리며, \
                    초대 코드가 폐기되어 더 이상 입장할 수 없다.

                    **시작 조건**
                    - 요청한 회원이 방장이어야 한다.
                    - 방장 포함 상장을 마친 참여자가 3명 이상이고, 대기자(상장 전)가 없어야 한다.
                    - 리그 상세 조회의 `startable`이 true일 때 시작 버튼을 활성화하면 된다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data.leagueId`·`data.startedAt`(KST).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"204"` | 없는 리그이거나 생성 후 7일이 지나 만료된 대기실 | 메시지 표시 후 목록으로 이동 |
                    | 200 | `"004"` | 입장하지 않은 리그 | 메시지 표시 |
                    | 200 | `"206"` | 참여자 3명 미만이거나 대기자가 있음 | 대기실 정보를 다시 조회 |
                    | 200 | `"207"` | 방장이 아님 | 메시지 표시 |
                    | 200 | `"208"` | 이미 시작된 리그 (중복 클릭 등) | 리그 화면으로 이동 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/api/leagues/{leagueId}/start")
    public Result<StartLeagueResponse> start(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId) {
        return Result.success(StartLeagueResponse.from(
                leagueStartUseCase.start(loginMember.memberId(), leagueId)));
    }
}
