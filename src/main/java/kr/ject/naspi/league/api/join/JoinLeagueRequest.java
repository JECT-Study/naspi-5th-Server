package kr.ject.naspi.league.api.join;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 초대 코드로 대기실 입장 요청.
 *
 * @param inviteCode 초대 코드 (영문·숫자 7자리, 대소문자 구분 없음)
 */
public record JoinLeagueRequest(
        @Schema(description = "초대 코드 (영문·숫자 7자리, 대소문자 구분 없음)", example = "A1B2C3D")
        @NotBlank(message = "초대 코드를 입력해야 합니다.") String inviteCode) {
}
