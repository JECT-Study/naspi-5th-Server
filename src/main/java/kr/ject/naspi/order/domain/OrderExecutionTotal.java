package kr.ject.naspi.order.domain;

/**
 * 주문 1건의 체결 합계. 주문 목록에서 체결 단가를 정하는 데 쓴다.
 *
 * @param orderId 주문 ID
 * @param quantity 총 체결 수량
 * @param amount 총 체결 금액 (체결가 × 수량의 합)
 * @param minPrice 최저 체결가. 체결이 없으면 0
 * @param maxPrice 최고 체결가. 체결이 없으면 0
 */
public record OrderExecutionTotal(long orderId, long quantity, long amount, long minPrice,
        long maxPrice) {

    /**
     * 체결이 없는 주문의 합계를 만든다.
     *
     * @param orderId 주문 ID
     * @return 수량·금액이 0인 합계
     */
    public static OrderExecutionTotal none(long orderId) {
        return new OrderExecutionTotal(orderId, 0, 0, 0, 0);
    }

    /**
     * 체결 단가가 한 가지인지 확인한다. 체결이 없으면 {@code false}이다.
     *
     * @return 체결이 있고 모든 체결가가 같으면 {@code true}
     */
    public boolean hasSinglePrice() {
        return quantity > 0 && minPrice == maxPrice;
    }

    /**
     * 체결 단가. 정책 (docs/policies/v1/domain/order.md "체결 단가 산정 및 표시"): 한 가지면 주당 체결 단가,
     * 둘 이상이면 {@code 총 체결 금액 ÷ 총 체결 수량}을 원 미만 절사한 평균 체결 단가, 체결 수량이 0주면
     * 제공하지 않는다.
     *
     * @return 체결 단가(원). 체결 수량이 0이면 {@code null}
     */
    public Long executionPrice() {
        if (quantity == 0) {
            return null;
        }
        return hasSinglePrice() ? minPrice : amount / quantity;
    }
}
