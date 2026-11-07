package kr.ject.naspi.member.api.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원가입 결과.
 *
 * @param memberId 가입한 회원 ID
 */
public record SignupResponse(
        @Schema(description = "가입한 회원 ID", example = "7") long memberId) {
}
