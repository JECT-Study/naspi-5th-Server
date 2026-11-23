package kr.ject.naspi.market.application;

/**
 * 거래일의 기준가와 상·하한가. 다른 모듈이 주문 가격 범위를 확인할 때 쓴다.
 *
 * @param basePrice 기준가 (원)
 * @param lowerLimitPrice 하한가 (원)
 * @param upperLimitPrice 상한가 (원)
 */
public record DailyPriceLimit(long basePrice, long lowerLimitPrice, long upperLimitPrice) {

    /**
     * 가격이 범위 안인지 확인한다. 상한가·하한가와 같은 가격도 범위 안이다.
     *
     * @param price 가격 (원)
     * @return 하한가 이상 상한가 이하면 {@code true}
     */
    public boolean contains(long price) {
        return lowerLimitPrice <= price && price <= upperLimitPrice;
    }
}
