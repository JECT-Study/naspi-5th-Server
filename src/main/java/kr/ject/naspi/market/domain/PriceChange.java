package kr.ject.naspi.market.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 기준가 대비 등락액·등락률 (docs/policies/v1/domain/market.md "시장").
 *
 * <p>등락률은 {@code (현재가 - 기준가) / 기준가 × 100}을 소수점 둘째 자리에서 반올림해 첫째 자리까지
 * 나타낸다. 정수여도 소수점 첫째 자리를 유지한다(scale 1).
 *
 * @param amount 등락액 (원). 현재가 - 기준가
 * @param rate 등락률 (%). scale 1
 */
public record PriceChange(long amount, BigDecimal rate) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 현재가와 기준가로 등락액·등락률을 계산한다.
     *
     * @param currentPrice 현재가 (원)
     * @param basePrice 기준가 (원, 0보다 커야 한다)
     * @return 등락액·등락률
     * @throws IllegalArgumentException 기준가가 0 이하인 경우
     */
    public static PriceChange of(long currentPrice, long basePrice) {
        if (basePrice <= 0) {
            throw new IllegalArgumentException("기준가는 0보다 커야 합니다: " + basePrice);
        }
        long amount = Math.subtractExact(currentPrice, basePrice);
        BigDecimal rate = BigDecimal.valueOf(amount).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(basePrice), 1, RoundingMode.HALF_UP);
        return new PriceChange(amount, rate);
    }
}
