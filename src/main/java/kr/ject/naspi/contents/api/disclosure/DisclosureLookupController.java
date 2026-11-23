package kr.ject.naspi.contents.api.disclosure;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.DisclosureQueryService;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공시 목록·상세 조회.
 */
@Tag(name = "공시", description = "공시 작성·삭제·조회")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks/{stockId}/disclosures")
@RequiredArgsConstructor
public class DisclosureLookupController {

    private static final String FIELDS = """
            - 작성 정보: `tradingDay`(작성한 거래일 1~7), `createdAt`(KST), `authorPlayerId`. \
            공시 작성자는 항상 그 종목의 상장자이므로 작성자 이름은 종목 조회의 `listerName`을 쓴다. \
            `mine`이 `true`이면 내가 작성한 공시다 (삭제 버튼 노출).
            - 반응: `likeCount`(좋아요=호재), `dislikeCount`(싫어요=악재), `myReaction`(`LIKE`, \
            `DISLIKE`, 반응 없으면 `null`).
            - 이미지 `expiresAt`은 이미지 저장 만료가 아니라 **`signedUrl`의 만료 일시**다 (발급 후 5분). \
            지나면 그 URL은 403을 돌려주므로 목록·상세를 다시 조회해 새 URL을 받는다. \
            이미 화면에 그려진 이미지는 계속 보이고, 이미지 파일도 그대로 남아 있다.
            """;

    private final DisclosureQueryService disclosureQueryService;

    /**
     * 종목의 공시를 최신순으로 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param cursor 이전 응답의 {@code nextCursor}. 첫 페이지면 생략
     * @param size 한 페이지 개수
     * @return 공시 목록 한 페이지
     * @throws NaspiBizException 리그에 입장하지 않은 회원인 경우
     */
    @Operation(
            summary = "종목별 공시 목록",
            description = """
                    종목에 등록된 공시를 최신순으로 조회한다. 삭제된 공시는 나오지 않는다. \
                    무한 스크롤용 커서 페이징이다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그에 입장한 회원만 조회할 수 있다. 종료된 리그도 조회할 수 있다.
                    - `cursor`: 첫 페이지는 생략, 다음 페이지는 이전 응답의 `nextCursor`.
                    - `size`: 1~50, 기본 20. 범위를 벗어나면 HTTP 400.

                    **성공 시** HTTP 200, `code` `"000"`, `data.items`에 공시 목록, \
                    `data.nextCursor`에 다음 커서 (`null`이면 마지막 페이지).
                    - `images`: 첨부 이미지 전체 (첨부 순서, 없으면 빈 배열). 상세와 같은 모양이다.
                    """ + FIELDS + """

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 400 | `"002"` | `size`가 1~50을 벗어남 | 요청 값 확인 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping
    public Result<DisclosurePageResponse> findByStock(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "" + DisclosureQueryService.DEFAULT_PAGE_SIZE)
            @Min(1) @Max(DisclosureQueryService.MAX_PAGE_SIZE) int size) {
        return Result.success(DisclosurePageResponse.from(disclosureQueryService.findByStock(
                loginMember.memberId(), leagueId, stockId, cursor, size)));
    }

    /**
     * 공시 상세를 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @return 공시 상세
     * @throws NaspiBizException 리그에 입장하지 않았거나 공시가 없거나 삭제된 경우
     */
    @Operation(
            summary = "공시 상세",
            description = """
                    공시의 제목, 본문, 첨부 이미지 전체, 작성 정보, 반응, AI 뉴스를 조회한다. \
                    삭제된 공시는 조회할 수 없다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 리그에 입장한 회원만 조회할 수 있다. 종료된 리그도 조회할 수 있다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 공시 상세.
                    - `images`: 첨부 순서대로 전체 이미지 (없으면 빈 배열).
                    - `news`: 공시로 만든 AI 뉴스. `status`가 `PENDING`(생성 중), `PUBLISHED`(발행), \
                    `SKIPPED`(기사로 쓸 내용이 없어 미생성), `FAILED`(생성 실패) 중 하나다. \
                    `title`, `body`, `sentiment`(`POSITIVE`=호재, `NEGATIVE`=악재)는 \
                    `PUBLISHED`일 때만 있고 나머지 상태에서는 `null`이다. \
                    뉴스가 없는 공시는 `news`가 `null`이다.
                    """ + FIELDS + """

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"607"` | 공시가 없거나 삭제됨 (다른 리그·종목의 공시 포함) | 안내 후 목록으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 (리그가 없는 경우 포함) | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/{disclosureId}")
    public Result<DisclosureDetailResponse> getDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long disclosureId) {
        return Result.success(DisclosureDetailResponse.from(disclosureQueryService.getDetail(
                loginMember.memberId(), leagueId, stockId, disclosureId)));
    }
}
