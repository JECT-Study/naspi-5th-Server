package kr.ject.naspi.order.domain;

import java.util.List;

/**
 * 신규 주문 매칭 결과.
 *
 * @param executions 체결 목록. 체결 순서대로 나열한다
 * @param selfTradePrevented 자전거래 금지로 신규 주문의 잔여 수량이 취소되었는지 여부
 */
public record MatchResult(List<Execution> executions, boolean selfTradePrevented) {

    /**
     * 체결된 수량 합계.
     *
     * @return 체결 수량 합계
     */
    public long executedQuantity() {
        return executions.stream().mapToLong(Execution::quantity).sum();
    }
}
