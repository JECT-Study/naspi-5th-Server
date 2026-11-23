package kr.ject.naspi.contents.api.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.NewsQueryService;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 종목별 뉴스 목록·상세 조회.
 */
@Tag(name = "뉴스", description = "종목별 AI 뉴스 조회")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks/{stockId}/news")
@RequiredArgsConstructor
public class NewsController {

    private static final String FIELDS = """
            - 뉴스: `title`, `body`, `sentiment`(`POSITIVE`=호재, `NEGATIVE`=악재), \
            `disclosureId`(원본 공시 ID. 공시가 삭제돼도 뉴스는 남으며, 이때 공시 상세는 `"607"`).
            - 일시(KST): `tradingDay`(원본 공시를 작성한 거래일 1~7), `createdAt`(뉴스 생성 요청 일시), \
            `publishedAt`(발행 일시).
            """;

    private final NewsQueryService newsQueryService;

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
                    `data.nextCursor`에 다음 커서 (`null`이면 마지막 페이지).
                    """ + FIELDS + """

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 400 | `"002"` | `size`가 1~50을 벗어남 | 요청 값 확인 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping
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
                    발행된 뉴스의 제목, 본문, 호재/악재, 발행 정보, 원본 공시 ID를 조회한다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그에 입장한 회원만 조회할 수 있다. 종료된 리그도 조회할 수 있다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 뉴스 상세.
                    """ + FIELDS + """

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"610"` | 뉴스가 없거나 발행되지 않음 (다른 리그·종목의 뉴스 포함) | 안내 후 목록으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/{newsId}")
    public Result<NewsResponse> getDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long newsId) {
        return Result.success(NewsResponse.from(newsQueryService.getDetail(
                loginMember.memberId(), leagueId, stockId, newsId)));
    }
}
