package kr.ject.naspi.contents.api.comment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.NewsCommentService;
import kr.ject.naspi.contents.application.NewsQueryService;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 뉴스 댓글 작성·조회·삭제.
 */
@Tag(name = "뉴스 댓글", description = "뉴스 댓글 작성·조회·삭제")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks/{stockId}/news/{newsId}/comments")
@RequiredArgsConstructor
public class NewsCommentController {

    private final NewsCommentService commentService;

    /**
     * 뉴스에 댓글을 작성한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param newsId 뉴스 ID
     * @param request 댓글 내용
     * @return 작성한 댓글
     * @throws NaspiBizException 내용이 올바르지 않거나 리그가 진행 중이 아니거나 뉴스가 없는 경우
     */
    @Operation(
            summary = "뉴스 댓글 작성",
            description = """
                    발행된 뉴스에 댓글을 작성한다. 이미지 첨부와 대댓글은 없다. \
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
                    | 200 | `"610"` | 뉴스가 없거나 발행되지 않음 | 목록 새로고침 |
                    | 200 | `"204"` | 리그가 없음 | 안내 후 홈으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 400 | `"002"` | `content` 누락 | 요청 값 확인 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping
    public Result<NewsCommentResponse> write(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long newsId,
            @Valid @RequestBody WriteNewsCommentRequest request) {
        return Result.success(NewsCommentResponse.from(commentService.write(
                loginMember.memberId(), leagueId, stockId, newsId, request.content())));
    }

    /**
     * 뉴스의 댓글을 최신순으로 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param newsId 뉴스 ID
     * @param cursor 이전 응답의 {@code nextCursor}. 첫 페이지면 생략
     * @param size 한 페이지 개수
     * @return 댓글 목록 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않았거나 뉴스가 없는 경우
     */
    @Operation(
            summary = "뉴스 댓글 목록",
            description = """
                    뉴스의 댓글을 최신순으로 조회한다. 삭제된 댓글은 나오지 않는다. \
                    무한 스크롤용 커서 페이징이다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그에 입장한 회원만 조회할 수 있다. 종료된 리그도 조회할 수 있다.
                    - `cursor`: 첫 페이지는 생략, 다음 페이지는 이전 응답의 `nextCursor`.
                    - `size`: 1~50, 기본 20. 범위를 벗어나면 HTTP 400.

                    **성공 시** HTTP 200, `code` `"000"`, `data.items`에 댓글 목록, \
                    `data.nextCursor`에 다음 커서 (`null`이면 마지막 페이지).
                    - `authorName`: 작성자 계정 이름. 탈퇴한 작성자는 `"탈퇴한 사용자"`.
                    - `mine`이 `true`이면 내가 쓴 댓글이다 (삭제 버튼 노출).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"610"` | 뉴스가 없거나 발행되지 않음 | 목록으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 400 | `"002"` | `size`가 1~50을 벗어남 | 요청 값 확인 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping
    public Result<NewsCommentPageResponse> findByNews(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long newsId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "" + NewsQueryService.DEFAULT_PAGE_SIZE)
            @Min(1) @Max(NewsQueryService.MAX_PAGE_SIZE) int size) {
        return Result.success(NewsCommentPageResponse.from(commentService.findByNews(
                loginMember.memberId(), leagueId, stockId, newsId, cursor, size)));
    }

    /**
     * 작성자 본인의 댓글을 삭제한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param newsId 뉴스 ID
     * @param commentId 댓글 ID
     * @return 성공 응답
     * @throws NaspiBizException 댓글이 없거나 작성자가 아닌 경우
     */
    @Operation(
            summary = "뉴스 댓글 삭제",
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
                    | 200 | `"617"` | 댓글이 없거나 이미 삭제됨 (다른 뉴스의 댓글 포함) | 목록 새로고침 |
                    | 200 | `"618"` | 작성자가 아님 | 삭제 버튼 숨김 |
                    | 200 | `"610"` | 뉴스가 없음 (다른 종목 경로 포함) | 목록으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/{commentId}/delete")
    public Result<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long newsId,
            @PathVariable long commentId) {
        commentService.delete(loginMember.memberId(), leagueId, stockId, newsId, commentId);
        return Result.success();
    }
}
