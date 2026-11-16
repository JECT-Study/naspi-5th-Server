package kr.ject.naspi.order.domain;

/**
 * 매매 구분.
 */
public enum OrderSide {

    /** 매수. */
    BUY,

    /** 매도. */
    SELL;

    /**
     * 반대 매매 구분을 반환한다.
     *
     * @return 매수면 매도, 매도면 매수
     */
    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
