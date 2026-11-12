package kr.ject.naspi.league.api.info;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대 코드로 리그 간단 정보 조회. 입장 전에 어떤 리그인지 확인하는 용도다.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장, 리그 조회")
@RestController
@RequiredArgsConstructor
public class LeagueInfoController {

    private final LeagueQueryService leagueQueryService;

    /**
     * 초대 코드로 리그 간단 정보를 조회한다.
     *
     * @param inviteCode 초대 코드 (대소문자 구분 없음)
     * @return 리그 이름, 상태, 입장 인원, 참여자 수
     * @throws NaspiBizException 초대 코드에 해당하는 대기실이 없는 경우
     */
    @Operation(
            summary = "초대 코드로 리그 정보 조회",
            description = """
                    초대 코드로 리그 이름, 상태, 입장한 사용자 수, 참여자 수를 조회한다. \
                    입장 전에 어떤 리그인지 확인하는 용도로, 로그인한 회원이면 누구나 조회할 수 있다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 초대 코드: 영문·숫자 7자리. 대소문자를 구분하지 않고 앞뒤 공백은 무시한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 다음 값.
                    - `name`: 리그 이름
                    - `status`: `WAITING`(대기실), `STARTED`(진행 중), `ENDED`(종료)
                    - `playerCount`: 입장한 사용자 수 (참여자 + 대기자, 최대 10)
                    - `participantCount`: 참여자 수 (상장을 마친 사용자 수)

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"201"` | 없는 코드이거나 이미 시작된 리그 | 메시지 표시 후 다시 입력 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/api/leagues/invite-codes/{inviteCode}")
    public Result<LeagueInfoResponse> getInfo(@PathVariable String inviteCode) {
        LeagueSummary summary = leagueQueryService.getSummary(inviteCode);
        return Result.success(new LeagueInfoResponse(summary.leagueId(), summary.name(),
                summary.status(), summary.playerCount(), summary.participantCount()));
    }
}
