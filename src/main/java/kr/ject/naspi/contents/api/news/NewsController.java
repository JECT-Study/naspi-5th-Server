package kr.ject.naspi.contents.api.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.NewsQueryService;
import kr.ject.naspi.contents.application.NewsRegenerationService;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 종목별 뉴스 목록·상세 조회.
 */
@Tag(name = "뉴스", description = "종목별 AI 뉴스 조회")
@RestController
@RequestMapping("/api/leagues/{leagueId}")
@RequiredArgsConstructor
public class NewsController {

    private static final String FIELDS = """
            - 뉴스: `title`, `body`, `sentiment`(`POSITIVE`=호재, `NEGATIVE`=악재).
            - 일시(KST): `tradingDay`(원본 공시를 작성한 거래일 1~7), `createdAt`(뉴스 생성 요청 일시), \
            `publishedAt`(발행 일시).
            - 댓글: `commentCount`(삭제되지 않은 댓글 수). 목록은 `previewComment`(가장 먼저 등록된 댓글 1개, \
            없으면 `null`), 상세는 `previewComment` 대신 `comments`(댓글 전체 배열, 오래된 순, 페이징 없음)를 \
            준다. 댓글 항목은 댓글 목록 API의 항목과 같은 모양이다.
            """;

    private final NewsQueryService newsQueryService;
    private final NewsRegenerationService newsRegenerationService;

    /**
     * 종목의 발행된 뉴스를 최신순으로 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param cursor 이전 응답의 {@code nextCursor}. 첫 페이지면 생략
     * @param size 한 페이지 개수
     * @return 뉴스 목록 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않은 회원인 경우
     */
    @Operation(
            summary = "종목별 뉴스 목록",
            description = """
                    종목의 발행된 뉴스를 최신순으로 조회한다. 생성 중·미생성·실패한 뉴스는 나오지 않는다. \
                    무한 스크롤용 커서 페이징이다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그에 입장한 회원만 조회할 수 있다. 종료된 리그도 조회할 수 있다.
                    - `cursor`: 첫 페이지는 생략, 다음 페이지는 이전 응답의 `nextCursor`.
                    - `size`: 1~50, 기본 20. 범위를 벗어나면 HTTP 400.

                    **성공 시** HTTP 200, `code` `"000"`, `data.items`에 뉴스 목록, \
                    `data.nextCursor`에 다음 커서 (`null`이면 마지막 페이지). \
                    원본 공시는 목록에 없고 상세에서 준다.
                    """ + FIELDS + """

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 400 | `"002"` | `size`가 1~50을 벗어남 | 요청 값 확인 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/stocks/{stockId}/news")
    public Result<NewsPageResponse> findByStock(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "" + NewsQueryService.DEFAULT_PAGE_SIZE)
            @Min(1) @Max(NewsQueryService.MAX_PAGE_SIZE) int size) {
        return Result.success(NewsPageResponse.from(newsQueryService.findByStock(
                loginMember.memberId(), leagueId, stockId, cursor, size)));
    }

    /**
     * 뉴스 상세를 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param newsId 뉴스 ID
     * @return 뉴스 상세
     * @throws NaspiBizException 리그에 입장하지 않았거나 뉴스가 없거나 발행되지 않은 경우
     */
    @Operation(
            summary = "뉴스 상세",
            description = """
                    발행된 뉴스의 제목, 본문, 호재/악재, 발행 정보, 댓글 전체, 원본 공시를 조회한다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그에 입장한 회원만 조회할 수 있다. 종료된 리그도 조회할 수 있다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 뉴스 상세. 목록 항목과 같은 필드에서 \
                    `previewComment` 대신 `comments`를 주고 `disclosure`가 더해진다.
                    - `disclosure`: 원본 공시 (`disclosureId`, `title`, `body`, `createdAt`). \
                    공시 없이 만든 뉴스이거나 원본 공시가 삭제됐으면 `null`이다.
                    """ + FIELDS + """

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"610"` | 뉴스가 없거나 발행되지 않음 (다른 리그·종목의 뉴스 포함) | 안내 후 목록으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/stocks/{stockId}/news/{newsId}")
    public Result<NewsDetailResponse> getDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long newsId) {
        return Result.success(NewsDetailResponse.from(newsQueryService.getDetail(
                loginMember.memberId(), leagueId, stockId, newsId)));
    }

    /**
     * 생성에 실패한 공시의 뉴스를 다시 생성하도록 요청한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @return 재생성을 요청한 뉴스 ID와 상태
     * @throws NaspiBizException 작성자가 아니거나, 실패한 뉴스가 아니거나, 1분 안에 다시 요청한 경우 등
     */
    @Operation(
            summary = "뉴스 다시 생성",
            description = """
                    자동 재시도까지 실패(`FAILED`)한 공시의 AI 뉴스를 공시 작성자가 다시 생성하도록 \
                    요청한다. 뉴스는 곧바로 `PENDING`(생성 중)이 되고, 생성은 서버에서 비동기로 진행되며 \
                    첫 생성과 같이 자동 재시도가 적용된다. 결과는 공시 상세의 `news.status`로 확인한다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 공시 작성자만, 삭제되지 않은 공시에서, 진행 중인 리그에서만 요청할 수 있다.
                    - 같은 뉴스는 1분에 한 번만 요청할 수 있다. 요청 본문은 없다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 `newsId`, `status`(`PENDING`).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"611"` | 공시 작성자가 아님 | 버튼 숨김 |
                    | 200 | `"612"` | 생성에 실패한 뉴스가 아님 (생성 중·발행·미생성) | 공시 상세 새로고침 |
                    | 200 | `"613"` | 직전 요청 후 1분이 지나지 않음 | 잠시 후 다시 시도 안내 |
                    | 200 | `"614"` | 진행 중인 리그가 아님 (대기실, 종료) | 버튼 비활성화 |
                    | 200 | `"607"` | 공시가 없거나 삭제됨 (다른 리그·종목의 공시 포함) | 목록으로 |
                    | 200 | `"610"` | 공시에 뉴스가 없음 | 공시 상세 새로고침 |
                    | 200 | `"204"` | 리그가 없음 | 안내 후 홈으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/stocks/{stockId}/disclosures/{disclosureId}/news/regenerate")
    public Result<RegeneratedNewsResponse> regenerate(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long disclosureId) {
        return Result.success(RegeneratedNewsResponse.from(newsRegenerationService.regenerate(
                loginMember.memberId(), leagueId, stockId, disclosureId)));
    }
}
