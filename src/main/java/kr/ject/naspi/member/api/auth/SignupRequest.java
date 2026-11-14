package kr.ject.naspi.member.api.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 회원가입 요청. 카카오 이름에 한글이 없어 이름을 직접 입력하는 경우에 쓴다.
 *
 * @param name 이름 (최대 5자 한글)
 */
public record SignupRequest(
        @Schema(description = "이름 (1~5자 한글, 자음·모음만 있는 글자도 한 글자)", example = "홍길동")
        @NotBlank(message = "이름을 입력해야 합니다.") String name) {
}
