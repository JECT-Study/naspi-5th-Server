package kr.ject.naspi.contents.api.reaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.DisclosureReactionService;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공시 반응 (호재·악재).
 */
@Tag(name = "공시 반응", description = "공시에 호재·악재 반응 토글")
@RestController
@RequiredArgsConstructor
public class DisclosureReactionController {

    private final DisclosureReactionService reactionService;

    /**
     * 공시의 호재·악재 버튼을 토글한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @param request 누른 반응
     * @return 토글 후 집계와 내 반응
     * @throws NaspiBizException 리그가 진행 중이 아니거나 공시가 없는 경우
     */
    @Operation(
            summary = "공시 반응 토글 (호재·악재)",
            description = """
                    누른 반응 버튼을 그대로 보내면 서버가 토글한다. 현재 반응 상태를 몰라도 된다.

                    | 현재 내 반응 | 누른 버튼 | 결과 `myReaction` |
                    |---|---|---|
                    | 없음 | `LIKE` | `LIKE` (등록) |
                    | `LIKE` | `LIKE` | `null` (취소) |
                    | `LIKE` | `DISLIKE` | `DISLIKE` (변경) |

                    공시 1건에 한 사람당 반응은 하나다. 본인 공시에도 반응할 수 있다. \
                    반응은 AI 뉴스 생성과 내용에 영향을 주지 않는다.

                    **요청** (`application/json`)
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 진행 중인 리그에서만 반응할 수 있다.
                    - `type`: 누른 버튼. `"LIKE"`(호재) 또는 `"DISLIKE"`(악재). 누락·다른 값이면 HTTP 400.
                    - 토글이라 같은 요청을 두 번 보내면 원래대로 돌아간다. 응답이 오기 전까지 버튼을 \
                    비활성화하고, 화면은 응답 값으로 갱신한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 토글 후 집계 \
                    (`disclosureId`, `likeCount`, `dislikeCount`, `myReaction`).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"607"` | 공시가 없거나 삭제됨 (다른 리그·종목의 공시 포함) | 목록 새로고침 |
                    | 200 | `"609"` | 리그 시작 전 (대기실) | 반응 버튼 비활성화 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 안내 후 리그 결과로 |
                    | 200 | `"204"` | 리그가 없음 | 안내 후 홈으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/api/leagues/{leagueId}/stocks/{stockId}/disclosures/{disclosureId}/reaction")
    public Result<DisclosureReactionResponse> toggle(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long disclosureId,
            @Valid @RequestBody ReactToDisclosureRequest request) {
        return Result.success(DisclosureReactionResponse.from(reactionService.toggle(
                loginMember.memberId(), leagueId, stockId, disclosureId, request.type())));
    }
}
