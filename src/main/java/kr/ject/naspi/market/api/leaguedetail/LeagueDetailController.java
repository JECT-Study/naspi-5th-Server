package kr.ject.naspi.market.api.leaguedetail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.market.application.LeagueDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리그 상세 조회. 플레이어 목록에 종목 정보를 합치므로 market 모듈에 둔다.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장, 리그 조회")
@RestController
@RequiredArgsConstructor
public class LeagueDetailController {

    private final LeagueDetailService leagueDetailService;

    /**
     * 리그 플레이어 목록과 각 참여자의 종목, 리그 시작 가능 여부를 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @return 리그 상세 정보
     * @throws NaspiBizException 리그가 없거나, 리그에 입장하지 않은 회원인 경우
     */
    @Operation(
            summary = "리그 상세 조회",
            description = """
                    리그에 입장한 사용자(방장·참여자·대기자) 목록과 각 참여자의 종목, \
                    리그 시작 가능 여부를 조회한다. 해당 리그에 입장한 회원만 조회할 수 있다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 다음 값.
                    - `startable`: 리그 시작 가능 여부 (참여자 3명 이상, 대기자 0명)
                    - `players`: 참여일시 순 목록
                      - `host`: 방장 여부, `listed`: 상장 완료 여부 (true 참여자, false 대기자)
                      - `joinedAt`·`listedAt`: 참여일시·상장일시 (KST), 대기자는 `listedAt`이 `null`
                      - `stock`: 상장한 종목 (`stockId`, `name`, `description`), 대기자는 `null`
                      - `name`: 회원 이름, 탈퇴한 회원이면 `null`

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 메시지 표시 |
                    | 200 | `"204"` | 리그가 없음 | 메시지 표시 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/api/leagues/{leagueId}/detail")
    public Result<LeagueDetailResponse> getDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId) {
        return Result.success(LeagueDetailResponse.from(
                leagueDetailService.getDetail(loginMember.memberId(), leagueId)));
    }
}
