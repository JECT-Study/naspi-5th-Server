package kr.ject.naspi.contents.api.disclosure;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.DisclosureService;
import kr.ject.naspi.contents.application.ImageFile;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공시 작성·삭제.
 */
@Tag(name = "공시", description = "공시 작성·삭제·조회")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks/{stockId}/disclosures")
@RequiredArgsConstructor
public class DisclosureController {

    private final DisclosureService disclosureService;

    /**
     * 자신이 상장한 종목에 공시를 작성한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param request 공시 작성 요청
     * @return 작성된 공시
     * @throws NaspiBizException 제목·본문·이미지가 규칙에 맞지 않거나, 리그가 진행 중이 아니거나,
     *         본인이 상장한 종목이 아닌 경우
     */
    @Operation(
            summary = "공시 작성",
            description = """
                    자신이 상장한 종목에 공시를 작성한다. 작성 횟수 제한은 없고, 작성한 공시는 수정할 수 없다. \
                    작성을 마치면 공시 기반 AI 뉴스 생성을 요청한다 (뉴스는 비동기로 생성되며 실패해도 공시는 유지).

                    **요청** (`multipart/form-data`)
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 진행 중인 리그에서, 본인이 상장한 종목에만 작성할 수 있다.
                    - `title`: 1~30자. `body`: 1~300자. 공백만 입력하면 빈 값으로 본다.
                    - `images`: 선택. 같은 이름으로 파일을 여러 개 보낸다 (보낸 순서가 첨부 순서). \
                    JPEG(.jpg/.jpeg)·PNG만, 최대 4개, 1장 최대 50MB. \
                    확장자·Content-Type·실제 파일 내용이 모두 맞아야 한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 작성된 공시 \
                    (`disclosureId`, `stockId`, `title`, `body`, `tradingDay`, `createdAt`, \
                    `imageIds`). \
                    이미지 URL은 공시 조회 API에서 받는다.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"600"` | JPEG·PNG가 아니거나 파일 내용이 확장자와 다름 | 다른 이미지 선택 |
                    | 200 | `"601"` | 빈 파일이거나 50MB 초과 | 다른 이미지 선택 |
                    | 200 | `"602"` | 이미지 5개 이상 | 4개 이하로 선택 |
                    | 200 | `"603"` | 제목이 비었거나 30자 초과 (누락 포함) | 메시지 표시 후 다시 입력 |
                    | 200 | `"604"` | 본문이 비었거나 300자 초과 (누락 포함) | 메시지 표시 후 다시 입력 |
                    | 200 | `"605"` | 본인이 상장한 종목이 아님 | 안내 후 이전 화면으로 |
                    | 200 | `"606"` | 진행 중인 리그가 아님 (대기실, 종료) | 안내 후 이전 화면으로 |
                    | 200 | `"204"` | 리그가 없음 | 안내 후 홈으로 |
                    | 200 | `"304"` | 해당 리그에 종목이 없음 | 안내 후 이전 화면으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DisclosureResponse> publish(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @ModelAttribute PublishDisclosureRequest request) {
        return Result.success(DisclosureResponse.from(disclosureService.publish(
                loginMember.memberId(), leagueId, stockId, request.title(), request.body(),
                toImageFiles(request.images()))));
    }

    /**
     * 자신이 작성한 공시를 삭제한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @return 성공 응답
     * @throws NaspiBizException 공시가 없거나 작성자가 아닌 경우
     */
    @Operation(
            summary = "공시 삭제",
            description = """
                    자신이 작성한 공시를 삭제한다. 삭제한 공시와 공시에 달린 댓글은 더 이상 조회되지 않는다. \
                    공시를 바탕으로 만든 AI 뉴스와 이미 체결된 거래는 그대로 유지된다. 첨부 이미지도 함께 삭제된다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 요청 본문은 없다.
                    - 리그 상태와 관계없이 삭제할 수 있다 (종료된 리그의 공시 포함).

                    **성공 시** HTTP 200, `code` `"000"`, `data` 없음.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"607"` | 공시가 없거나 이미 삭제됨 (다른 리그·종목의 공시 포함) | 목록 새로고침 |
                    | 200 | `"608"` | 작성자가 아님 | 안내 (삭제 버튼은 작성자에게만 노출) |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/{disclosureId}/delete")
    public Result<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @PathVariable long disclosureId) {
        disclosureService.delete(loginMember.memberId(), leagueId, stockId, disclosureId);
        return Result.success();
    }

    private static List<ImageFile> toImageFiles(List<MultipartFile> images) {
        if (images == null) {
            return List.of();
        }
        // 파일 선택 없이 빈 파트만 보내는 클라이언트가 있어 이름 없는 빈 파트는 첨부하지 않은 것으로 본다.
        return images.stream()
                .filter(file -> !(file.isEmpty() && isBlank(file.getOriginalFilename())))
                .map(DisclosureController::toImageFile)
                .toList();
    }

    private static ImageFile toImageFile(MultipartFile file) {
        try {
            return new ImageFile(file.getOriginalFilename(), file.getContentType(),
                    file.getBytes());
        } catch (IOException e) {
            throw new NaspiRuntimeException("업로드 파일 읽기 실패", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
