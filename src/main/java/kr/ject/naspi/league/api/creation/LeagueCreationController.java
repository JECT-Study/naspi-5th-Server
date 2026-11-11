package kr.ject.naspi.league.api.creation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.CreatedLeague;
import kr.ject.naspi.league.application.LeagueCreationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리그 생성.
 */
@Tag(name = "리그", description = "리그 생성, 초대 코드로 대기실 입장")
@RestController
@RequiredArgsConstructor
public class LeagueCreationController {

    private final LeagueCreationUseCase leagueCreationUseCase;

    /**
     * 리그를 만들고 방장의 종목을 상장한다.
     *
     * @param loginMember 로그인한 회원 (방장이 된다)
     * @param request 리그 이름과 방장의 종목
     * @return 생성된 리그 ID와 초대 코드
     * @throws NaspiBizException 리그 이름이나 종목 이름·소개가 규칙에 맞지 않는 경우
     */
    @Operation(
            summary = "리그 생성",
            description = """
                    리그 이름과 방장의 종목(이름·소개)을 받아 리그를 만든다. \
                    방장의 상장까지 마쳐야 생성이 완료되므로 한 번에 처리하고, 하나라도 규칙에 맞지 않으면 \
                    리그가 만들어지지 않는다. 생성되면 대기실과 공통 초대 코드 1개가 발급된다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그 이름: 1~12자, 모든 문자 가능, 다른 리그와 이름이 같아도 된다.
                    - 종목 이름: 앞뒤 공백 제거 후 1~12자, 글자·숫자·공백만 (특수문자·이모지 불가).
                    - 종목 소개: 1~50자.

                    **성공 시** HTTP 200, `code` `"000"`, `data.leagueId`에 리그 ID, \
                    `data.inviteCode`에 초대 코드 (영문 대문자·숫자 7자리).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"200"` | 리그 이름이 비었거나 12자 초과 | 메시지 표시 후 다시 입력 |
                    | 200 | `"205"` | 이미 리그 5개(대기실 + 진행 중)에 참여 중 | 메시지 표시 |
                    | 200 | `"300"` | 종목 이름 규칙 위반 | 메시지 표시 후 다시 입력 |
                    | 200 | `"301"` | 종목 소개가 비었거나 50자 초과 | 메시지 표시 후 다시 입력 |
                    | 400 | `"002"` | 필드 누락 | 다시 입력 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/api/leagues")
    public Result<CreateLeagueResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody CreateLeagueRequest request) {
        CreatedLeague created = leagueCreationUseCase.create(loginMember.memberId(),
                request.leagueName(), request.stockName(), request.stockDescription());
        return Result.success(new CreateLeagueResponse(created.leagueId(), created.inviteCode()));
    }
}
