package kr.ject.naspi.websocket.api.response.alert;

/** 알림 종류. */
public enum AlertType {

    /** 대기 중이던 내 주문이 다른 사용자의 주문으로 체결됨. */
    TRADE_EXECUTED,
    /** 새 거래일이 시작되어 대기 중이던 내 주문이 만료됨. */
    ORDER_EXPIRED,
    /** 새 거래일이 시작됨 (만료된 내 주문 없음). */
    TRADING_DAY_CHANGED,
    /** 공시 게시. */
    DISCLOSURE,
    /** 뉴스 게시. */
    NEWS,
    /** 리그 진행 (시작, 종료 등). */
    LEAGUE
}
