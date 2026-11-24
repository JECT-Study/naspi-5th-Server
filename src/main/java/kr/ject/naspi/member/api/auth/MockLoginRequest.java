package kr.ject.naspi.member.api.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 모킹 로그인 요청 (테스트 전용).
 *
 * @param userNo 테스트 회원 번호. 같은 번호는 항상 같은 회원이다
 * @param name 처음 로그인해 가입할 때 쓸 이름. 없으면 기본 이름을 쓰고, 이미 가입한 회원이면 무시한다
 */
public record MockLoginRequest(
        @Schema(description = "테스트 회원 번호 (1~1000). 같은 번호는 항상 같은 회원", example = "1")
        @NotNull(message = "테스트 회원 번호를 입력해야 합니다.")
        @Min(value = 1, message = "테스트 회원 번호는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "테스트 회원 번호는 1000 이하여야 합니다.") Integer userNo,
        @Schema(description = "가입할 때 쓸 이름 (1~5자 한글). 이미 가입한 회원이면 무시", example = "테스터",
                nullable = true) String name) {

    /** 이름을 보내지 않았을 때 쓰는 이름. */
    static final String DEFAULT_NAME = "테스터";

    /**
     * 테스트 회원의 카카오 회원 번호. 실제 카카오 회원 번호(양수)와 겹치지 않도록 음수를 쓴다.
     *
     * @return 음수 카카오 회원 번호
     */
    long mockKakaoId() {
        return -userNo.longValue();
    }

    /**
     * 가입할 때 쓸 이름.
     *
     * @return 요청한 이름, 없으면 기본 이름
     */
    String nameOrDefault() {
        return name == null ? DEFAULT_NAME : name;
    }
}
