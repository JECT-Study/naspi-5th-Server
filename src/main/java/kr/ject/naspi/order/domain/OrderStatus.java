package kr.ject.naspi.order.domain;

import java.util.Set;

/**
 * 주문 상태와 허용되는 전이.
 *
 * <pre>
 * 대기 → 부분 체결 → 체결 완료
 *  │        │
 *  └────────┴→ 취소 / 만료   (체결 완료·취소·만료는 종료 상태)
 * </pre>
 *
 * <p>부분 체결 상태에서 추가로 체결되어도 부분 체결을 유지한다. 시장가 주문의 잔여 수량 취소와
 * 자전거래로 인한 신규 주문 잔여 수량 취소는 취소로 처리한다.
 */
public enum OrderStatus {

    /** 대기. 아직 체결된 수량이 없다. */
    PENDING,

    /** 부분 체결. 일부 체결되었고 잔여 수량이 남아 있다. */
    PARTIALLY_FILLED,

    /** 체결 완료. 잔여 수량이 없다. */
    FILLED,

    /** 취소. 사용자 취소, 시장가 잔여 취소, 자전거래 취소, 리그 종료·탈퇴 취소를 포함한다. */
    CANCELED,

    /** 만료. 지정가 주문의 미체결 잔여 수량이 다음 거래일 09:00 KST에 만료된 경우. */
    EXPIRED;

    /**
     * 체결 대기 중인지 확인한다. 대기와 부분 체결이 해당한다.
     *
     * @return 잔여 수량이 체결을 기다리는 상태면 {@code true}
     */
    public boolean isOpen() {
        return this == PENDING || this == PARTIALLY_FILLED;
    }

    /**
     * 주어진 상태로 전이할 수 있는지 확인한다.
     *
     * @param next 전이할 상태
     * @return 전이할 수 있으면 {@code true}
     */
    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    private Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PENDING -> Set.of(PARTIALLY_FILLED, FILLED, CANCELED, EXPIRED);
            case PARTIALLY_FILLED -> Set.of(PARTIALLY_FILLED, FILLED, CANCELED, EXPIRED);
            case FILLED, CANCELED, EXPIRED -> Set.of();
        };
    }
}
