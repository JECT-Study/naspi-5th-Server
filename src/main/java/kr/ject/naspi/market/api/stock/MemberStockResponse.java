package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.ject.naspi.market.application.MemberStock;

/**
 * 리그의 종목 이름과 소개.
 *
 * @param stockId 종목 ID
 * @param name 종목 이름
 * @param description 종목 소개
 * @param listerName 상장자 이름. 탈퇴한 회원이면 null
 * @param mine 조회한 회원 본인의 종목인지 여부
 */
public record MemberStockResponse(
        @Schema(description = "종목 ID", example = "1") long stockId,
        @Schema(description = "종목 이름", example = "수빈전자") String name,
        @Schema(description = "종목 소개", example = "성실함이 주력 사업입니다.") String description,
        @Schema(description = "상장자 이름. 탈퇴한 회원이면 null", example = "수빈") String listerName,
        @Schema(description = "내가 상장한 종목인지 여부", example = "true") boolean mine) {

    static MemberStockResponse from(MemberStock stock) {
        return new MemberStockResponse(stock.stockId(), stock.name(), stock.description(),
                stock.listerName(), stock.mine());
    }
}
