package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대기실 종목 수정 요청. 값의 규칙 검증은 상장과 같이 도메인이 맡는다.
 *
 * @param name 새 종목 이름
 * @param description 새 종목 소개
 */
public record EditStockRequest(
        @Schema(description = "종목 이름 (앞뒤 공백 제거 후 1~12자, 글자·숫자·공백만)", example = "수빈바이오")
        String name,
        @Schema(description = "종목 소개 (1~50자)", example = "꼼꼼함이 주력 사업입니다.")
        String description) {
}
