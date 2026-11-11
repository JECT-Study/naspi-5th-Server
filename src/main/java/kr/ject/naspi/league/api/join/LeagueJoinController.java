package kr.ject.naspi.league.api.join;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.JoinedLeague;
import kr.ject.naspi.league.application.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대 코드로 대기실 입장.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장")
@RestController
@RequiredArgsConstructor
public class LeagueJoinController {

    private final LeagueService leagueService;

    /**
     * 초대 코드로 리그 대기실에 대기자로 입장한다.
     *
     * @param loginMember 로그인한 회원
     * @param request 초대 코드
     * @return 입장한 리그 ID
     * @throws NaspiBizException 유효하지 않은 초대 코드이거나 이미 입장한 리그인 경우
     */
    @Operation(
            summary = "초대 코드로 대기실 입장",
            description = """
                    초대 코드로 리그 대기실에 입장한다. 입장하면 대기자가 되고, 종목을 상장하면 참여자가 된다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 초대 코드: 영문·숫자 7자리. 대소문자를 구분하지 않고 앞뒤 공백은 무시한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data.leagueId`에 입장한 리그 ID.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"201"` | 없는 코드이거나 이미 시작된 리그 | 메시지 표시 후 다시 입력 |
                    | 200 | `"202"` | 이미 입장한 리그 | 해당 리그 대기실로 이동 |
                    | 200 | `"203"` | 리그 인원(참여자 + 대기자) 10명 | 메시지 표시 |
                    | 400 | `"002"` | `inviteCode` 누락·공백 | 다시 입력 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/api/leagues/join")
    public Result<JoinLeagueResponse> join(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody JoinLeagueRequest request) {
        JoinedLeague joined = leagueService.join(loginMember.memberId(), request.inviteCode());
        return Result.success(new JoinLeagueResponse(joined.leagueId()));
    }
}
