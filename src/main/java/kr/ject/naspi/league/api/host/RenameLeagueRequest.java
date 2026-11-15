package kr.ject.naspi.league.api.host;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 리그 이름 수정 요청.
 *
 * <p>글자 수 규칙은 도메인이 검증해 {@code INVALID_LEAGUE_NAME}으로 응답하므로 여기서는 누락만 확인한다.
 *
 * @param name 새 리그 이름 (1~12자)
 */
public record RenameLeagueRequest(
        @Schema(description = "새 리그 이름 (1~12자, 모든 문자 가능, 다른 리그와 중복 가능)",
                example = "우리끼리 주식왕")
        @NotNull(message = "리그 이름을 입력해야 합니다.") String name) {
}
