package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 종목 소개 수정 요청. 값의 규칙 검증은 상장과 같이 도메인이 맡는다.
 *
 * @param description 새 종목 소개
 */
public record ChangeStockDescriptionRequest(
        @Schema(description = "종목 소개 (1~50자)", example = "성실함이 주력 사업입니다.")
        String description) {
}
