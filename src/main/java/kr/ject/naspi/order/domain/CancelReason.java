package kr.ject.naspi.order.domain;

/**
 * 주문 취소·만료 사유. 이름이 DB 값({@code orders.cancel_reason})과 API 응답 코드가 되며, 표시 문구는
 * 클라이언트가 매핑한다.
 *
 * <p>PostgreSQL에서 Hibernate가 enum 값 목록 CHECK 제약을 만들 수 있고 {@code ddl-auto: update}는 이를
 * 바꾸지 않는다. 상수를 추가하거나 이름을 바꾸면 배포 전에 SQL로 제약을 고친다.
 */
public enum CancelReason {

    /** 사용자 직접 취소 ("직접 취소", ORD-07). */
    USER_REQUESTED,

    /** 시장가 일부 체결 후 잔량 취소 ("체결 가능 수량 부족", ORD-05). */
    MARKET_PARTIALLY_FILLED,

    /** 시장가 체결 없이 전량 취소 ("체결 가능 수량 없음", ORD-05). */
    MARKET_NOT_FILLED,

    /** 자전거래 방지 ("내 주문 간 체결 방지", ORD-04). */
    SELF_TRADE_PREVENTED,

    /** 거래일 변경 만료 ("거래일 변경으로 만료", ORD-09). 상태 {@code EXPIRED}와 짝이다. */
    TRADING_DAY_EXPIRED,

    /** 리그 종료. 사용처는 ORD-10에서 만든다. */
    LEAGUE_ENDED,

    /** 회원 탈퇴. 사용처는 ORD-11에서 만든다. */
    MEMBER_WITHDRAWN
}
