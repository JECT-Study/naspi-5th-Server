package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 종목 상장 요청.
 *
 * <p>값의 규칙 검증(길이, 허용 문자, 필수 여부)은 도메인({@code StockName}, {@code StockDescription})이
 * 맡아 비즈니스 오류 코드로 응답한다.
 *
 * @param name 종목 이름
 * @param description 종목 소개
 */
public record ListStockRequest(
        @Schema(description = "종목 이름 (앞뒤 공백 제거 후 1~12자, 글자·숫자·공백만)", example = "수빈전자")
        String name,
        @Schema(description = "종목 소개 (1~50자)", example = "성실함이 주력 사업입니다.")
        String description) {
}
