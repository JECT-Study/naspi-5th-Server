package kr.ject.naspi.contents.api.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.contents.application.ImageFile;
import kr.ject.naspi.contents.application.ReportService;
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
 * 제보 작성.
 */
@Tag(name = "제보", description = "제보 작성")
@RestController
@RequestMapping("/api/leagues/{leagueId}/stocks/{stockId}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 다른 참여자의 종목에 제보를 작성한다.
     *
     * @param loginMember 로그인한 회원
     * @param leagueId 리그 ID
     * @param stockId 대상 종목 ID
     * @param request 제보 작성 요청
     * @return 작성된 제보
     * @throws NaspiBizException 본문·이미지가 규칙에 맞지 않거나, 리그가 진행 중이 아니거나,
     *         본인이 상장한 종목인 경우
     */
    @Operation(
            summary = "제보 작성",
            description = """
                    다른 참여자의 종목에서 일어난 일상적인 사건을 제보한다. 작성 횟수 제한은 없고, 작성한 제보는 \
                    수정할 수 없다. 작성을 마치면 제보 기반 AI 뉴스 생성을 요청한다 \
                    (뉴스는 비동기로 생성되며 실패하거나 만들어지지 않아도 제보는 유지).

                    **요청** (`multipart/form-data`)
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 진행 중인 리그에서, 본인이 상장하지 않은 종목에만 작성할 수 있다 \
                    (상장자가 탈퇴한 종목도 가능).
                    - `body`: 1~300자. 공백만 입력하면 빈 값으로 본다. 제목은 없다.
                    - `images`: 선택. 같은 이름으로 파일을 여러 개 보낸다 (보낸 순서가 첨부 순서). \
                    JPEG(.jpg/.jpeg)·PNG만, 최대 4개, 1장 최대 50MB. \
                    확장자·Content-Type·실제 파일 내용이 모두 맞아야 한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data`에 작성된 제보 \
                    (`reportId`, `stockId`, `body`, `tradingDay`, `createdAt`, `imageIds`, \
                    `newsId`). \
                    `newsId`는 생성 중인 뉴스의 ID이며, AI가 발행해야 뉴스 목록·상세에 나온다 \
                    (제보 내용이 부족하거나 생성에 실패하면 나오지 않는다).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"600"` | JPEG·PNG가 아니거나 파일 내용이 확장자와 다름 | 다른 이미지 선택 |
                    | 200 | `"601"` | 빈 파일이거나 50MB 초과 | 다른 이미지 선택 |
                    | 200 | `"602"` | 이미지 5개 이상 | 4개 이하로 선택 |
                    | 200 | `"619"` | 본문이 비었거나 300자 초과 (누락 포함) | 메시지 표시 후 다시 입력 |
                    | 200 | `"620"` | 본인이 상장한 종목 (자기 종목은 공시로 작성) | 안내 후 이전 화면으로 |
                    | 200 | `"621"` | 리그 시작 전 (대기실) | 안내 후 이전 화면으로 |
                    | 200 | `"212"` | 이미 종료된 리그 (종료 시각 이후 포함) | 안내 후 리그 결과로 |
                    | 200 | `"204"` | 리그가 없음 | 안내 후 홈으로 |
                    | 200 | `"304"` | 해당 리그에 종목이 없음 | 안내 후 이전 화면으로 |
                    | 200 | `"004"` | 리그에 입장하지 않은 회원 | 안내 후 홈으로 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ReportResponse> submit(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable long leagueId,
            @PathVariable long stockId,
            @ModelAttribute SubmitReportRequest request) {
        return Result.success(ReportResponse.from(reportService.submit(
                loginMember.memberId(), leagueId, stockId, request.body(),
                toImageFiles(request.images()))));
    }

    private static List<ImageFile> toImageFiles(List<MultipartFile> images) {
        if (images == null) {
            return List.of();
        }
        // 파일 선택 없이 빈 파트만 보내는 클라이언트가 있어 이름 없는 빈 파트는 첨부하지 않은 것으로 본다.
        return images.stream()
                .filter(file -> !(file.isEmpty() && isBlank(file.getOriginalFilename())))
                .map(ReportController::toImageFile)
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
