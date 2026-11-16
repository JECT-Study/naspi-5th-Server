package kr.ject.naspi.market.domain;

/**
 * 거래일의 상·하한가 (docs/policies/v1/domain/market.md "가격제한폭").
 *
 * <p>기준가 대비 ±30% 범위에서 주문·체결할 수 있다. 100원 호가 단위를 반영하기 위해
 * 가격제한폭 금액은 {@code 기준가 × 30%}에서 100원 미만을 절사한다. 같은 기준가에서는 항상 같은
 * 결과를 낸다.
 *
 * @param lower 하한가 (원)
 * @param upper 상한가 (원)
 */
public record PriceLimit(long lower, long upper) {

    /** 호가 단위 (원). */
    public static final long TICK_SIZE = 100;

    /** 가격제한폭 비율 (%). */
    public static final long LIMIT_PERCENT = 30;

    /**
     * 기준가로 상·하한가를 계산한다.
     *
     * @param basePrice 거래일의 기준가 (100원 이상, 100원 단위)
     * @return 상·하한가
     * @throws IllegalArgumentException 기준가가 100원 미만이거나 100원 단위가 아닌 경우
     */
    public static PriceLimit of(long basePrice) {
        if (basePrice < TICK_SIZE || basePrice % TICK_SIZE != 0) {
            throw new IllegalArgumentException("기준가는 100원 이상의 100원 단위여야 합니다: " + basePrice);
        }
        long amount = Math.multiplyExact(basePrice, LIMIT_PERCENT) / 100 / TICK_SIZE * TICK_SIZE;
        return new PriceLimit(basePrice - amount, basePrice + amount);
    }

    /**
     * 가격이 범위 안인지 확인한다. 상한가·하한가와 같은 가격도 범위 안이다.
     *
     * @param price 가격 (원)
     * @return 하한가 이상 상한가 이하면 {@code true}
     */
    public boolean contains(long price) {
        return lower <= price && price <= upper;
    }

    /**
     * 가격을 범위 안으로 맞춘다. 상한가보다 높으면 상한가, 하한가보다 낮으면 하한가를 돌려준다.
     *
     * @param price 가격 (원)
     * @return 범위 안으로 맞춘 가격
     */
    public long clamp(long price) {
        return Math.max(lower, Math.min(upper, price));
    }
}
