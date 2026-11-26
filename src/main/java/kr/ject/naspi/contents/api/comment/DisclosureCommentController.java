package kr.ject.naspi.contents.api.comment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.DisclosureCommentService;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공시 댓글 작성·조회·삭제. 뉴스 댓글({@link NewsCommentController})과 같은 절차다.
 */
@Tag(name = "공시 댓글", description = "공시 댓글 작성·조회·삭제")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks/{stockId}/disclosures/{disclosureId}/comments")
@RequiredArgsConstructor
public class DisclosureCommentController {

    private final DisclosureCommentService commentService;

    /**
     * 공시에 댓글을 작성한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @param request 댓글 내용
     * @return 작성한 댓글
     * @throws NaspiBizException 내용이 올바르지 않거나 리그가 진행 중이 아니거나 공시가 없거나 삭제된 경우
     */
    @Operation(
            summary = "공시 댓글 작성",
            description = """
                    삭제되지 않은 공시에 댓글을 작성한다. 이미지 첨부와 대댓글은 없다. \
                    작성자 이름은 계정 이름으로 표시된다.

                    **요청** (`application/json`)
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 진행 중인 리그에서만 작성할 수 있다. 종목과 관계없이 리그 참여자면 작성할 수 있다.
                    - `content`: 1~100자. 공백만 있으면 안 된다. 누락이면 HTTP 400.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 작성한 댓글 \
                    (`commentId`, `content`, `authorPlayerId`, `authorName`, `mine`, `createdAt`).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"615"` | 댓글이 비었거나 100자 초과 | 입력 안내 |
                    | 200 | `"616"` | 진행 중인 리그가 아님 (대기실, 종료) | 입력창 비활성화 |
                    | 200 | `"607"` | 공시가 없거나 삭제됨 | 목록 새로고침 |
                    | 200 | `"204"` | 리그가 없음 | 안내 후 홈으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 400 | `"002"` | `content` 누락 | 요청 값 확인 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping
    public Result<CommentResponse> write(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long disclosureId,
            @Valid @RequestBody WriteCommentRequest request) {
        return Result.success(CommentResponse.from(commentService.write(
                loginMember.memberId(), leagueId, stockId, disclosureId, request.content())));
    }

    /**
     * 공시의 댓글을 오래된 순으로 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @return 댓글 전체 (오래된 순)
     * @throws NaspiBizException 리그에 입장하지 않았거나 공시가 없거나 삭제된 경우
     */
    @Operation(
            summary = "공시 댓글 목록",
            description = """
                    공시의 댓글을 오래된 순으로 모두 조회한다. 삭제된 댓글은 나오지 않는다. 페이징하지 않는다. \
                    공시 상세의 `comments`와 같은 목록이므로 댓글 작성·삭제 후 다시 불러올 때 쓴다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그에 입장한 회원만 조회할 수 있다. 종료된 리그도 조회할 수 있다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 댓글 배열 (오래된 순, 없으면 빈 배열).
                    - `authorName`: 작성자 계정 이름. 탈퇴한 작성자는 `"탈퇴한 사용자"`.
                    - `mine`이 `true`이면 내가 쓴 댓글이다 (삭제 버튼 노출).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"607"` | 공시가 없거나 삭제됨 | 목록으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 안내 후 리그 결과로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping
    public Result<List<CommentResponse>> findByDisclosure(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long disclosureId) {
        return Result.success(commentService.findByTarget(loginMember.memberId(), leagueId,
                stockId, disclosureId).stream().map(CommentResponse::from).toList());
    }

    /**
     * 작성자 본인의 댓글을 삭제한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @param commentId 댓글 ID
     * @return 성공 응답
     * @throws NaspiBizException 댓글이 없거나 작성자가 아닌 경우
     */
    @Operation(
            summary = "공시 댓글 삭제",
            description = """
                    작성자 본인의 댓글을 삭제한다. 삭제된 댓글은 목록에서 빠진다. \
                    리그가 종료된 뒤에도 삭제할 수 있다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 요청 본문은 없다.

                    **성공 시** HTTP 200, `code` `"000"`.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"617"` | 댓글이 없거나 이미 삭제됨 (다른 공시의 댓글 포함) | 목록 새로고침 |
                    | 200 | `"618"` | 작성자가 아님 | 삭제 버튼 숨김 |
                    | 200 | `"607"` | 공시가 없거나 삭제됨 (다른 종목 경로 포함) | 목록으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 안내 후 리그 결과로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/{commentId}/delete")
    public Result<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long disclosureId,
            @PathVariable long commentId) {
        commentService.delete(loginMember.memberId(), leagueId, stockId, disclosureId, commentId);
        return Result.success();
    }
}
