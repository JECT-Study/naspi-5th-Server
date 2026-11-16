package kr.ject.naspi.order.domain;

/**
 * 시장가 주문의 보호 가격 계산 (docs/policies/v1/domain/order.md "시장가 주문").
 *
 * <p>주문 접수 시 최우선 상대 호가를 기준으로 5% 이내에서만 체결한다.
 * 매수는 최우선 매도 호가보다 5% 높은 가격, 매도는 최우선 매수 호가보다 5% 낮은 가격을 계산한 뒤
 * 100원 미만을 절사하고, 그 가격이 당일 상·하한가를 벗어나면 해당 상·하한가를 적용한다.
 * 보호 범위는 접수 시 한 번 확정하며 이후 호가 변동은 반영하지 않는다.
 */
public final class MarketOrderProtection {

    /** 보호 범위 비율 (%). */
    public static final long PROTECTION_PERCENT = 5;

    private MarketOrderProtection() {
    }

    /**
     * 보호 가격을 계산한다.
     *
     * @param side 주문의 매매 구분
     * @param bestOppositePrice 최우선 상대 호가. 매수는 최우선 매도 호가, 매도는 최우선 매수 호가
     * @param lowerLimit 당일 하한가
     * @param upperLimit 당일 상한가
     * @return 매수는 체결 가능한 최고 가격(보호최고가), 매도는 체결 가능한 최저 가격
     */
    public static long calculate(OrderSide side, long bestOppositePrice, long lowerLimit,
            long upperLimit) {
        long percent = side == OrderSide.BUY ? 100 + PROTECTION_PERCENT : 100 - PROTECTION_PERCENT;
        long raw = Math.multiplyExact(bestOppositePrice, percent) / 100;
        long floored = raw / Order.TICK_SIZE * Order.TICK_SIZE;
        return Math.max(lowerLimit, Math.min(upperLimit, floored));
    }
}
