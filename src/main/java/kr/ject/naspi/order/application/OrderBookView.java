package kr.ject.naspi.order.application;

import java.util.List;

/**
 * 호가 조회 결과. 주문자와 주문 수는 노출하지 않는다.
 *
 * @param currentPrice 종목 현재가 (원)
 * @param asks 매도 호가. 낮은 가격부터 최대 5단계
 * @param bids 매수 호가. 높은 가격부터 최대 5단계
 */
public record OrderBookView(long currentPrice, List<PriceLevel> asks, List<PriceLevel> bids) {

    /**
     * 호가 한 단계.
     *
     * @param price 주문 가격 (원)
     * @param quantity 이 가격의 미체결 잔여 수량 합계 (주)
     */
    public record PriceLevel(long price, long quantity) {
    }
}
