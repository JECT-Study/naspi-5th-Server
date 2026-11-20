package kr.ject.naspi.league.api.mine;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.league.application.LeagueQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내가 입장한 리그 목록 조회.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장, 리그 조회")
@RestController
@RequiredArgsConstructor
public class MyLeagueController {

    private final LeagueQueryService leagueQueryService;

    /**
     * 로그인한 회원이 입장한 대기실·진행 중·종료 리그를 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @return 내 입장 일시 최신순 리그 목록
     */
    @Operation(
            summary = "내 리그 목록 조회",
            description = """
                    로그인한 회원이 입장한 대기실·진행 중·종료 리그를 한 목록으로 조회한다. \
                    상장 전인 대기자로 입장한 대기실도 포함하고, 삭제·만료된 대기실과 \
                    나가거나 추방된 리그는 나오지 않는다. 페이지네이션은 없다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 리그 배열. \
                    내 입장 일시 최신순(같으면 리그 ID 내림차순)이며, 입장한 리그가 없으면 빈 배열.
                    - `leagueId`·`name`: 리그 ID·이름
                    - `status`: `WAITING`(대기실), `STARTED`(진행 중), `ENDED`(종료). 저장된 상태 그대로
                    - `playerCount`: 입장 인원 (참여자 + 대기자), `participantCount`: 참여자 수
                    - `host`: 내가 방장인지 여부, `listed`: 내 상장 완료 여부 (false면 대기자)
                    - `joinedAt`·`createdAt`: 내 입장 일시·리그 생성 일시 (KST)
                    - `waitingRoomExpiresAt`: 대기실 만료 일시 (KST). 대기실이 아니면 `null`
                    - `startedAt`·`endsAt`: 리그 시작·종료 일시 (KST). 시작 전이면 `null`
                    - `tradingDay`: 진행 중인 리그의 현재 거래일. 그 외 `null`
                      - `dayNumber`: 현재 거래일차 (1~7), `totalDays`: 전체 거래일 수 (7)
                      - `dayEndsAt`: 현재 거래일이 끝나는 일시 (다음 09:00)

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/api/leagues/me")
    public Result<List<MyLeagueResponse>> getMyLeagues(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember) {
        return Result.success(leagueQueryService.getMyLeagues(loginMember.memberId()).stream()
                .map(MyLeagueResponse::from).toList());
    }
}
