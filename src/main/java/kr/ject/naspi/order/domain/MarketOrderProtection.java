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

    /**
     * 시장가 매수에 예약할 현금을 계산한다 (docs/policies/v1/domain/asset.md "매수 주문의 현금 예약").
     *
     * <p>보호 범위 안의 매도 호가를 최우선부터 주문 수량까지 순서대로 적용해 금액을 합산하고, 호가가 모자란
     * 수량은 보호최고가로 계산한다. 접수 직전 호가창으로 계산하므로 매칭 결과와 같은 순서다.
     *
     * @param book 접수 직전 호가창
     * @param quantity 주문 수량. 양수
     * @param protectionPrice 보호최고가
     * @return 예약할 금액. {@code long} 범위를 넘으면 {@link Long#MAX_VALUE} (주문 가능 현금 부족으로 거절된다)
     */
    public static long buyReservationAmount(OrderBook book, long quantity, long protectionPrice) {
        long left = quantity;
        long amount = 0;
        try {
            for (Order ask : book.orders(OrderSide.SELL)) {
                if (left == 0 || ask.getPrice() > protectionPrice) {
                    break;
                }
                long applied = Math.min(left, ask.getRemainingQuantity());
                amount = Math.addExact(amount, Math.multiplyExact(ask.getPrice(), applied));
                left -= applied;
            }
            return Math.addExact(amount, Math.multiplyExact(protectionPrice, left));
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
