package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 종목 상장 결과.
 *
 * @param stockId 상장된 종목 ID
 */
public record ListStockResponse(
        @Schema(description = "상장된 종목 ID", example = "1") long stockId) {
}
