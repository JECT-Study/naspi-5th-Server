package kr.ject.naspi.league.api.lobby;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueDetailUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리그 상세 조회. 플레이어 목록에 각 참여자의 종목 정보를 합친다.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장, 리그 조회")
@RestController
@RequiredArgsConstructor
public class LeagueDetailController {

    private final LeagueDetailUseCase leagueDetailUseCase;

    /**
     * 리그 플레이어 목록과 각 참여자의 종목, 리그 시작 가능 여부, 현재 거래일을 조회한다.
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
                    리그 시작 가능 여부, 시작된 리그면 현재 거래일과 종료 일시를 조회한다. \
                    해당 리그에 입장한 회원만 조회할 수 있다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 다음 값.
                    - `name`: 리그 이름
                    - `inviteCode`: 대기실 초대 코드 (영문 대문자·숫자 7자리). \
                    리그가 시작되었거나 대기실이 만료되었으면 `null`
                    - `waitingRoomExpiresAt`: 대기실 만료 기한 (KST, 생성 후 7일). \
                    기한 이후 매일 09:00 만료 처리 전까지는 대기실로 유지된다. 대기실이 아니면 `null`
                    - `startable`: 리그 시작 가능 여부 (참여자 3명 이상, 대기자 0명)
                    - `tradingDay`: 현재 거래일과 리그 운영 기간 (KST). 대기실이면 `null`
                      - `dayNumber`: 현재 거래일차 (1~7). 매일 09:00에 1씩 늘고, \
                    00:00~09:00에 시작한 리그는 당일 09:00까지도 1일차
                      - `totalDays`: 전체 거래일 수 (7)
                      - `dayEndsAt`: 현재 거래일이 끝나는 일시 (다음 09:00, 7일차는 `endsAt`과 같음)
                      - `startedAt`·`endsAt`: 리그 시작·종료 일시
                    - `players`: 참여일시 순 목록
                      - `me`: 조회한 회원 본인 여부 (목록에서 정확히 1명)
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
                leagueDetailUseCase.getDetail(loginMember.memberId(), leagueId)));
    }
}
