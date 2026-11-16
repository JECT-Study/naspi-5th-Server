package kr.ject.naspi.order.api.place;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.ject.naspi.order.domain.OrderSide;

/**
 * 지정가 주문 요청.
 *
 * <p>가격·수량 규칙 검증은 도메인이 맡아 비즈니스 오류 코드로 응답한다. 누락된 숫자는 0으로 들어와
 * 같은 오류가 된다.
 *
 * @param stockId 종목 ID
 * @param side 매매 구분 ({@code BUY} 매수, {@code SELL} 매도)
 * @param price 1주당 주문 가격 (원)
 * @param quantity 주문 수량 (주)
 */
public record PlaceLimitOrderRequest(
        @Schema(description = "종목 ID", example = "1") long stockId,
        @Schema(description = "매매 구분 (BUY 매수, SELL 매도)", example = "BUY") OrderSide side,
        @Schema(description = "1주당 주문 가격 (100원 이상, 100원 단위)", example = "10000") long price,
        @Schema(description = "주문 수량 (1주 이상)", example = "10") long quantity) {
}
