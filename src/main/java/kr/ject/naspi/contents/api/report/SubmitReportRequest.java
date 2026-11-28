package kr.ject.naspi.contents.api.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 제보 작성 요청 ({@code multipart/form-data}).
 *
 * <p>값의 규칙 검증(길이, 형식, 개수)은 도메인과 {@code ImageService}가 맡아 비즈니스 오류 코드로 응답한다.
 *
 * @param body 본문
 * @param images 첨부 이미지 (선택, 첨부 순서대로)
 */
record SubmitReportRequest(
        @Schema(description = "제보 본문 (1~300자)", example = "점심에 라면 세 그릇을 드셨습니다.")
        String body,
        @Schema(description = "첨부 이미지 (선택). JPEG(.jpg/.jpeg)·PNG, 최대 4개, 1장 최대 50MB")
        List<MultipartFile> images) {
}
