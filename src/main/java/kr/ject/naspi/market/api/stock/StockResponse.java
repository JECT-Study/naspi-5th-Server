package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.ject.naspi.market.application.StockDetail;

/**
 * 수정된 종목 정보.
 *
 * @param stockId 종목 ID
 * @param name 종목 이름
 * @param description 종목 소개
 */
public record StockResponse(
        @Schema(description = "종목 ID", example = "1") long stockId,
        @Schema(description = "종목 이름", example = "수빈전자") String name,
        @Schema(description = "종목 소개", example = "성실함이 주력 사업입니다.") String description) {

    static StockResponse from(StockDetail detail) {
        return new StockResponse(detail.stockId(), detail.name(), detail.description());
    }
}
