package kr.ject.naspi.league.api.creation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 리그 생성 요청. 리그 이름과 방장의 종목을 함께 받는다.
 *
 * <p>글자 수 등 세부 규칙은 도메인이 검증해 각 규칙의 오류 코드로 응답하므로 여기서는 누락만 확인한다.
 *
 * @param leagueName 리그 이름 (1~12자)
 * @param stockName 방장의 종목 이름 (1~12자 글자·숫자·공백)
 * @param stockDescription 방장의 종목 소개 (1~50자)
 */
public record CreateLeagueRequest(
        @Schema(description = "리그 이름 (1~12자, 모든 문자 가능, 다른 리그와 중복 가능)",
                example = "우리끼리 주식왕")
        @NotNull(message = "리그 이름을 입력해야 합니다.") String leagueName,

        @Schema(description = "방장의 종목 이름 (앞뒤 공백 제거 후 1~12자, 글자·숫자·공백만)",
                example = "나스피")
        @NotNull(message = "종목 이름을 입력해야 합니다.") String stockName,

        @Schema(description = "방장의 종목 소개 (1~50자)", example = "성장 가능성 무한대")
        @NotNull(message = "종목 소개를 입력해야 합니다.") String stockDescription) {
}
