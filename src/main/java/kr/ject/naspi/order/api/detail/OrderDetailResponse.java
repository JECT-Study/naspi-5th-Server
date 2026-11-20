package kr.ject.naspi.order.api.detail;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.order.application.OrderDetail;

/**
 * 주문 1건의 상세 내역.
 *
 * @param orderId 주문 ID
 * @param stockId 종목 ID
 * @param stockName 종목 이름
 * @param side 매매 구분
 * @param type 주문 유형
 * @param orderedAt 접수 일시
 * @param quantity 최초 주문 수량
 * @param price 지정가 주문 가격
 * @param fills 체결 내역
 * @param cancellation 취소 내역
 * @param pendingQuantity 현재 대기 수량
 */
public record OrderDetailResponse(
        @Schema(description = "주문 ID", example = "11") long orderId,
        @Schema(description = "종목 ID", example = "1") long stockId,
        @Schema(description = "종목 이름", example = "김철수") String stockName,
        @Schema(description = "매매 구분", allowableValues = {"BUY", "SELL"}) String side,
        @Schema(description = "주문 유형", allowableValues = {"LIMIT", "MARKET"}) String type,
        @Schema(description = "주문 접수 일시 (KST)") LocalDateTime orderedAt,
        @Schema(description = "최초 주문 수량 (주)", example = "10") long quantity,
        @Schema(description = "지정가 주문 가격 (원). 시장가는 null", nullable = true,
                example = "10000") Long price,
        @Schema(description = "체결 내역. 체결 일시 오름차순. 체결 일시와 단가가 모두 같은 체결은 "
                + "수량을 합산해 1건으로 제공한다. 체결이 없으면 빈 배열") List<Fill> fills,
        @Schema(description = "취소 내역. 취소 수량이 없으면 null", nullable = true)
        Cancellation cancellation,
        @Schema(description = "현재 대기 수량 (주) = 최초 주문 수량 - 체결 수량 - 취소 수량. 없으면 null",
                nullable = true, example = "5") Long pendingQuantity) {

    /**
     * 체결 1건.
     *
     * @param executedAt 체결 일시
     * @param price 체결 단가
     * @param quantity 체결 수량
     * @param amount 실제 체결 금액
     */
    public record Fill(
            @Schema(description = "체결 일시 (KST)") LocalDateTime executedAt,
            @Schema(description = "체결 단가 (원)", example = "10000") long price,
            @Schema(description = "체결 수량 (주)", example = "3") long quantity,
            @Schema(description = "실제 체결 금액 (원) = 체결 단가 × 체결 수량", example = "30000")
            long amount) {
    }

    /**
     * 취소 내역.
     *
     * @param quantity 취소 수량
     * @param canceledAt 취소 일시
     * @param reason 취소 사유 코드
     */
    public record Cancellation(
            @Schema(description = "취소 수량 (주). 거래일 변경으로 만료된 수량 포함", example = "7")
            long quantity,
            @Schema(description = "취소 일시 (KST)") LocalDateTime canceledAt,
            @Schema(description = """
                    취소 사유 코드. 표시 문구는 클라이언트가 매핑한다.
                    - `USER_REQUESTED`: 직접 취소
                    - `MARKET_PARTIALLY_FILLED`: 체결 가능 수량 부족
                    - `MARKET_NOT_FILLED`: 체결 가능 수량 없음
                    - `SELF_TRADE_PREVENTED`: 내 주문 간 체결 방지
                    - `TRADING_DAY_EXPIRED`: 거래일 변경으로 만료
                    (`LEAGUE_ENDED`, `MEMBER_WITHDRAWN`은 ORD-10/11에서 추가)""",
                    allowableValues = {"USER_REQUESTED", "MARKET_PARTIALLY_FILLED",
                            "MARKET_NOT_FILLED", "SELF_TRADE_PREVENTED", "TRADING_DAY_EXPIRED"})
            String reason) {
    }

    /**
     * 주문 상세로 응답을 만든다.
     *
     * @param detail 주문 상세
     * @return 응답
     */
    static OrderDetailResponse from(OrderDetail detail) {
        OrderDetail.Cancellation cancellation = detail.cancellation();
        return new OrderDetailResponse(detail.orderId(), detail.stockId(), detail.stockName(),
                detail.side().name(), detail.type().name(), detail.orderedAt(), detail.quantity(),
                detail.price(),
                detail.fills().stream()
                        .map(fill -> new Fill(fill.executedAt(), fill.price(), fill.quantity(),
                                fill.amount()))
                        .toList(),
                cancellation == null ? null
                        : new Cancellation(cancellation.quantity(), cancellation.canceledAt(),
                                cancellation.reason().name()),
                detail.pendingQuantity());
    }
}
