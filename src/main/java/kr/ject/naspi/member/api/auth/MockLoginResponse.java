package kr.ject.naspi.member.api.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모킹 로그인 결과 (테스트 전용).
 *
 * @param memberId 로그인한 회원 ID
 */
public record MockLoginResponse(
        @Schema(description = "로그인한 회원 ID", example = "7") long memberId) {
}
