package kr.ject.naspi.order.api.list;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.order.application.OrderSummary;

/**
 * 주문 목록.
 *
 * @param orders 주문 목록. 접수 역순
 */
public record OrderListResponse(
        @Schema(description = "주문 목록. 접수 역순(최신 먼저). 없으면 빈 배열") List<Item> orders) {

    /**
     * 주문 1건.
     *
     * @param orderId 주문 ID
     * @param stockId 종목 ID
     * @param stockName 종목 이름
     * @param side 매매 구분
     * @param type 주문 유형
     * @param orderedAt 접수 일시
     * @param quantity 최초 주문 수량
     * @param price 지정가 주문 가격
     * @param filledQuantity 체결 수량
     * @param pendingQuantity 현재 대기 수량
     * @param canceledQuantity 취소 수량
     * @param executionPrice 체결 단가
     * @param averagedPrice 체결 단가가 평균 체결 단가인지 여부
     */
    public record Item(
            @Schema(description = "주문 ID", example = "11") long orderId,
            @Schema(description = "종목 ID", example = "1") long stockId,
            @Schema(description = "종목 이름", example = "김철수") String stockName,
            @Schema(description = "매매 구분", allowableValues = {"BUY", "SELL"}) String side,
            @Schema(description = "주문 유형", allowableValues = {"LIMIT", "MARKET"}) String type,
            @Schema(description = "주문 접수 일시 (KST)") LocalDateTime orderedAt,
            @Schema(description = "최초 주문 수량 (주)", example = "10") long quantity,
            @Schema(description = "지정가 주문 가격 (원). 시장가는 null", nullable = true,
                    example = "10000") Long price,
            @Schema(description = "체결 수량 (주). 없으면 null", nullable = true,
                    example = "3") Long filledQuantity,
            @Schema(description = "현재 대기 수량 (주). 없으면 null", nullable = true,
                    example = "5") Long pendingQuantity,
            @Schema(description = "취소 수량 (주). 거래일 변경으로 만료된 수량 포함. 없으면 null",
                    nullable = true, example = "2") Long canceledQuantity,
            @Schema(description = "체결 단가 (원). 체결 단가가 한 가지면 주당 체결 단가, 둘 이상이면 "
                    + "평균 체결 단가(총 체결 금액 ÷ 총 체결 수량, 원 미만 절사). 체결 수량이 0이면 null",
                    nullable = true, example = "10050") Long executionPrice,
            @Schema(description = "executionPrice가 평균 체결 단가이면 true", example = "false")
            boolean averagedPrice) {
    }

    /**
     * 주문 요약 목록으로 응답을 만든다.
     *
     * @param summaries 주문 요약 목록
     * @return 응답
     */
    static OrderListResponse from(List<OrderSummary> summaries) {
        return new OrderListResponse(summaries.stream()
                .map(s -> new Item(s.orderId(), s.stockId(), s.stockName(), s.side().name(),
                        s.type().name(), s.orderedAt(), s.quantity(), s.price(),
                        s.filledQuantity(), s.pendingQuantity(), s.canceledQuantity(),
                        s.executionPrice(), s.averagedPrice()))
                .toList());
    }
}
