package kr.ject.naspi.contents.api.feed;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.FeedItemType;
import kr.ject.naspi.contents.application.FeedQueryService;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리그의 공시·뉴스 통합 피드 조회.
 */
@Tag(name = "피드", description = "리그 공시·뉴스 통합 목록")
@RestController
@RequiredArgsConstructor
public class FeedController {

    private final FeedQueryService feedQueryService;

    /**
     * 리그의 공시와 발행된 뉴스를 등록 일시 최신순으로 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID. 생략하면 리그의 모든 종목
     * @param type 항목 종류. 생략하면 공시와 뉴스 모두
     * @param cursor 이전 응답의 {@code nextCursor}. 첫 페이지면 생략
     * @param size 한 페이지 개수
     * @return 피드 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않았거나 커서 형식이 올바르지 않은 경우
     */
    @Operation(
            summary = "리그 공시·뉴스 통합 피드",
            description = """
                    리그의 공시와 발행된 AI 뉴스를 한 목록에 등록 일시 최신순으로 조회한다. \
                    무한 스크롤용 커서 페이징이다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그에 입장한 회원만 조회할 수 있다. 종료된 리그도 조회할 수 있다.
                    - `stockId`: 그 종목의 공시·뉴스만. 생략하면 리그의 모든 종목.
                    - `type`: `DISCLOSURE`(공시만) 또는 `NEWS`(뉴스만). 생략하면 둘 다.
                    - `cursor`: 첫 페이지는 생략, 다음 페이지는 이전 응답의 `nextCursor`를 그대로 보낸다 \
                    (문자열). 필터를 바꾸면 첫 페이지부터 다시 조회한다.
                    - `size`: 1~50, 기본 5. 범위를 벗어나면 HTTP 400.

                    **성공 시** HTTP 200, `code` `"000"`, `data.items`에 피드 목록, \
                    `data.nextCursor`에 다음 커서 (`null`이면 마지막 페이지).
                    - 정렬: 공시는 작성 일시(`createdAt`), 뉴스는 발행 일시(`publishedAt`) 기준 최신순.
                    - 항목: `type`으로 구분한다. `DISCLOSURE`면 종목별 공시 목록\
                    (`GET .../stocks/{stockId}/disclosures`)의 항목과, `NEWS`면 종목별 뉴스 목록\
                    (`GET .../stocks/{stockId}/news`)의 항목과 같은 모양이다 (두 목록 항목에도 `type`이 있다). \
                    보이는 범위도 같다 (삭제된 공시 제외, 발행된 뉴스만, 공시가 삭제돼도 발행된 뉴스는 보임).
                    - 종목: 모든 항목에 종목 ID(`stockId`)와 종목 이름(`stockName`)이 있다.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 안내 후 리그 결과로 |
                    | 200 | `"002"` | `cursor` 형식이 올바르지 않음 | 첫 페이지부터 다시 조회 |
                    | 400 | `"002"` | `size`가 1~50을 벗어나거나 `type`이 올바르지 않음 | 요청 값 확인 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/api/leagues/{leagueId}/feed")
    public Result<FeedPageResponse> find(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @RequestParam(required = false) Long stockId,
            @RequestParam(required = false) FeedItemType type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "" + FeedQueryService.DEFAULT_PAGE_SIZE)
            @Min(1) @Max(FeedQueryService.MAX_PAGE_SIZE) int size) {
        return Result.success(FeedPageResponse.from(feedQueryService.find(
                loginMember.memberId(), leagueId, stockId, type, cursor, size)));
    }
}
