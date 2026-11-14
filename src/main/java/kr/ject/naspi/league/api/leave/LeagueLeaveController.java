package kr.ject.naspi.league.api.leave;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueLeaveUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리그 시작 전 대기실 나가기.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장")
@RestController
@RequiredArgsConstructor
public class LeagueLeaveController {

    private final LeagueLeaveUseCase leagueLeaveUseCase;

    /**
     * 리그가 시작되기 전에 대기실을 나간다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @return 나간 리그 ID와 대기실 삭제 여부
     * @throws NaspiBizException 이미 시작된 리그이거나 입장하지 않은 리그인 경우 등
     */
    @Operation(
            summary = "리그 나가기",
            description = """
                    리그가 시작되기 전에 대기실을 나간다. 상장한 종목이 있으면 함께 삭제되고, \
                    나간 뒤에는 초대 코드로 다시 입장할 수 있다.

                    **방장이 나가는 경우**
                    - 상장을 먼저 마친 참여자에게 방장이 넘어간다.
                    - 넘겨받을 참여자(상장 완료)가 없으면 대기실이 삭제되고 초대 코드가 폐기된다. \
                    이때 `data.waitingRoomDeleted`가 true이다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data.leagueId`·`data.waitingRoomDeleted`.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"204"` | 없는 리그이거나 만료·삭제된 대기실 | 메시지 표시 후 목록으로 이동 |
                    | 200 | `"004"` | 입장하지 않은 리그 (이미 나간 경우 포함) | 목록으로 이동 |
                    | 200 | `"208"` | 이미 시작된 리그 (시작 이후에는 나갈 수 없음) | 메시지 표시 후 리그 화면으로 이동 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/api/leagues/{leagueId}/leave")
    public Result<LeaveLeagueResponse> leave(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId) {
        return Result.success(LeaveLeagueResponse.from(
                leagueLeaveUseCase.leave(loginMember.memberId(), leagueId)));
    }
}
