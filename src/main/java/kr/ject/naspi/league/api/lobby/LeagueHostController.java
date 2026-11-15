package kr.ject.naspi.league.api.lobby;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueHostUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기실에서 방장만 할 수 있는 리그 관리: 리그 이름 수정, 리그 삭제, 플레이어 추방.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장")
@RestController
@RequiredArgsConstructor
public class LeagueHostController {

    private static final String COMMON_FAILURES = """
            | 200 | `"204"` | 없는 리그이거나 만료·삭제된 대기실 | 메시지 표시 후 목록으로 이동 |
            | 200 | `"004"` | 입장하지 않은 리그 | 목록으로 이동 |
            | 200 | `"208"` | 이미 시작된 리그 (시작 이후에는 방장 권한 없음) | 메시지 표시 후 리그 화면으로 이동 |
            | 200 | `"207"` | 방장이 아님 | 메시지 표시 |
            | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
            """;

    private final LeagueHostUseCase leagueHostUseCase;

    /**
     * 방장이 대기실에서 리그 이름을 바꾼다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param request 새 리그 이름
     * @return 리그 ID와 바뀐 이름
     * @throws NaspiBizException 방장이 아니거나, 이미 시작된 리그이거나, 이름 규칙에 맞지 않는 경우 등
     */
    @Operation(
            summary = "리그 이름 수정 (방장)",
            description = """
                    대기실에서 방장이 리그 이름을 바꾼다. 리그가 시작된 이후에는 바꿀 수 없다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그 이름: 1~12자, 모든 문자 가능, 다른 리그와 이름이 같아도 된다.

                    **성공 시** HTTP 200, `code` `"000"`, `data.leagueId`·`data.name`.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"200"` | 리그 이름이 비었거나 12자 초과 | 메시지 표시 후 다시 입력 |
                    | 400 | `"002"` | 이름 누락 | 다시 입력 |
                    """ + COMMON_FAILURES)
    @PostMapping("/api/leagues/{leagueId}/name")
    public Result<RenameLeagueResponse> rename(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @Valid @RequestBody RenameLeagueRequest request) {
        return Result.success(RenameLeagueResponse.from(
                leagueHostUseCase.rename(loginMember.memberId(), leagueId, request.name())));
    }

    /**
     * 방장이 대기실을 삭제한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @return 빈 성공 응답
     * @throws NaspiBizException 방장이 아니거나 이미 시작된 리그인 경우 등
     */
    @Operation(
            summary = "리그 삭제 (방장)",
            description = """
                    대기실에서 방장이 리그를 삭제한다. 초대 코드가 폐기되고, 남아 있던 플레이어도 \
                    더는 대기실에 접근할 수 없다 (이후 조회 시 `"204"`). 리그가 시작된 이후에는 삭제할 수 없다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    """ + COMMON_FAILURES)
    @PostMapping("/api/leagues/{leagueId}/delete")
    public Result<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId) {
        leagueHostUseCase.delete(loginMember.memberId(), leagueId);
        return Result.success();
    }

    /**
     * 방장이 대기실에서 다른 플레이어를 추방한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param playerId 추방할 플레이어 ID
     * @return 빈 성공 응답
     * @throws NaspiBizException 방장이 아니거나, 이미 시작된 리그이거나, 방장 자신을 추방하려는 경우 등
     */
    @Operation(
            summary = "플레이어 추방 (방장)",
            description = """
                    대기실에서 방장이 다른 플레이어를 추방한다. 상장을 마친 참여자와 상장 전인 대기자 \
                    모두 추방할 수 있고, 방장 자신은 추방할 수 없다 (방장은 리그 나가기를 쓴다). \
                    리그가 시작된 이후에는 추방할 수 없다.
                    `playerId`는 대기실 상세 조회(`GET /api/leagues/{leagueId}/detail`)의 \
                    `players[].playerId`이다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"209"` | 방장 자신을 추방하려 함 | 메시지 표시 |
                    | 200 | `"210"` | 리그에 없는 플레이어 (이미 나갔거나 추방됨) | 대기실 목록 새로고침 |
                    """ + COMMON_FAILURES)
    @PostMapping("/api/leagues/{leagueId}/players/{playerId}/kick")
    public Result<Void> kick(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long playerId) {
        leagueHostUseCase.kick(loginMember.memberId(), leagueId, playerId);
        return Result.success();
    }
}
