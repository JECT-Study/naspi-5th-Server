package kr.ject.naspi.contents.api.disclosure;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공시 작성 요청 ({@code multipart/form-data}).
 *
 * <p>값의 규칙 검증(길이, 형식, 개수)은 도메인과 {@code ImageService}가 맡아 비즈니스 오류 코드로 응답한다.
 *
 * @param title 제목
 * @param body 본문
 * @param images 첨부 이미지 (선택, 첨부 순서대로)
 */
record PublishDisclosureRequest(
        @Schema(description = "공시 제목 (1~30자)", example = "오늘 헬스장 등록했습니다")
        String title,
        @Schema(description = "공시 본문 (1~300자)", example = "3개월 끊었습니다. 체력이 곧 실적입니다.")
        String body,
        @Schema(description = "첨부 이미지 (선택). JPEG(.jpg/.jpeg)·PNG, 최대 4개, 1장 최대 50MB")
        List<MultipartFile> images) {
}
