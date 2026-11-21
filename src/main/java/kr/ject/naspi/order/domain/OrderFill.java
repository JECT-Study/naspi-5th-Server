package kr.ject.naspi.order.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주문 내역에 보여 주는 체결 1건. 체결 일시와 체결 단가가 모두 같은 체결은 수량을 합산해 1건으로 집계한다
 * (order.md "주문 내역 조회"). 상대방 정보는 담지 않는다.
 *
 * @param executedAt 체결 일시 (저장값 그대로)
 * @param price 체결 단가 (원)
 * @param quantity 체결 수량 (주). 합산된 수량
 * @param amount 실제 체결 금액 (원). 체결 단가 × 체결 수량
 */
public record OrderFill(LocalDateTime executedAt, long price, long quantity, long amount) {

    private record Key(LocalDateTime executedAt, long price) {
    }

    /**
     * 체결 일시와 단가가 같은 체결을 합산한다.
     *
     * @param trades 한 주문의 체결 기록. 체결 일시·ID 오름차순
     * @return 합산된 체결 내역. 처음 등장한 순서를 유지한다
     */
    public static List<OrderFill> group(List<Trade> trades) {
        Map<Key, OrderFill> grouped = new LinkedHashMap<>();
        for (Trade trade : trades) {
            Key key = new Key(trade.getExecutedAt(), trade.getPrice());
            grouped.merge(key,
                    new OrderFill(trade.getExecutedAt(), trade.getPrice(), trade.getQuantity(),
                            trade.getAmount()),
                    (a, b) -> new OrderFill(a.executedAt, a.price,
                            Math.addExact(a.quantity, b.quantity),
                            Math.addExact(a.amount, b.amount)));
        }
        return new ArrayList<>(grouped.values());
    }
}
