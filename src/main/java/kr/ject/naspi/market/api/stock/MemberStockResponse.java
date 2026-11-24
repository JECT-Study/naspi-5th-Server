package kr.ject.naspi.market.api.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import kr.ject.naspi.market.application.MemberStock;
import kr.ject.naspi.market.application.StockQuote;

/**
 * 리그의 종목 이름과 소개.
 *
 * @param stockId 종목 ID
 * @param name 종목 이름
 * @param description 종목 소개
 * @param listerName 상장자 이름. 시작된 리그에서 탈퇴한 상장자면 '탈퇴한 사용자'
 * @param mine 조회한 회원 본인의 종목인지 여부
 * @param currentPrice 현재가. 리그 시작 전이면 {@code null}
 * @param basePrice 기준가. 리그 시작 전이면 {@code null}
 * @param upperLimitPrice 상한가. 리그 시작 전이면 {@code null}
 * @param lowerLimitPrice 하한가. 리그 시작 전이면 {@code null}
 * @param changeAmount 기준가 대비 등락액. 리그 시작 전이면 {@code null}
 * @param changeRate 기준가 대비 등락률(%). 리그 시작 전이면 {@code null}
 * @param volume 현재 거래일 거래량. 리그 시작 전이면 {@code null}
 */
public record MemberStockResponse(
        @Schema(description = "종목 ID", example = "1") long stockId,
        @Schema(description = "종목 이름", example = "수빈전자") String name,
        @Schema(description = "종목 소개", example = "성실함이 주력 사업입니다.") String description,
        @Schema(description = "상장자 이름. 시작된 리그에서 탈퇴한 상장자면 '탈퇴한 사용자'",
                example = "수빈") String listerName,
        @Schema(description = "내가 상장한 종목인지 여부", example = "true") boolean mine,
        @Schema(description = "현재가(원). 리그 시작 전이면 null", example = "12500",
                nullable = true) Long currentPrice,
        @Schema(description = "기준가(원). 직전 거래일 종가, 첫 거래일은 시초가. 리그 시작 전이면 null",
                example = "10000", nullable = true) Long basePrice,
        @Schema(description = "상한가(원). 리그 시작 전이면 null", example = "13000",
                nullable = true) Long upperLimitPrice,
        @Schema(description = "하한가(원). 리그 시작 전이면 null", example = "7000",
                nullable = true) Long lowerLimitPrice,
        @Schema(description = "기준가 대비 등락액(원). 리그 시작 전이면 null", example = "2500",
                nullable = true) Long changeAmount,
        @Schema(description = "기준가 대비 등락률(%). 소수점 첫째 자리. 리그 시작 전이면 null",
                example = "25.0", nullable = true) BigDecimal changeRate,
        @Schema(description = "현재 거래일 거래량(주). 리그 시작 전이면 null", example = "5",
                nullable = true) Long volume) {

    static MemberStockResponse from(MemberStock stock) {
        StockQuote quote = stock.quote();
        if (quote == null) {
            return new MemberStockResponse(stock.stockId(), stock.name(), stock.description(),
                    stock.listerName(), stock.mine(), null, null, null, null, null, null, null);
        }
        return new MemberStockResponse(stock.stockId(), stock.name(), stock.description(),
                stock.listerName(), stock.mine(), quote.currentPrice(), quote.basePrice(),
                quote.upperLimitPrice(), quote.lowerLimitPrice(), quote.changeAmount(),
                quote.changeRate(), quote.volume());
    }
}
