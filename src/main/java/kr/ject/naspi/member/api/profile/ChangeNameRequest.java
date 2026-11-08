package kr.ject.naspi.member.api.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 회원 이름 수정 요청.
 *
 * @param name 새 이름 (최대 5자 한글)
 */
public record ChangeNameRequest(
        @Schema(description = "새 이름 (1~5자 한글)", example = "김철수")
        @NotBlank(message = "이름을 입력해야 합니다.") String name) {
}
