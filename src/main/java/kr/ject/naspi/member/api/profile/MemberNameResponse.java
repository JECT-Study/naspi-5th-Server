package kr.ject.naspi.member.api.profile;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 이름.
 *
 * @param name 이름
 */
public record MemberNameResponse(
        @Schema(description = "이름", example = "김철수") String name) {
}
