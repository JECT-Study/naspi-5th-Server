package kr.ject.naspi.websocket.api.response.alert;

/** 알림 종류. */
public enum AlertType {

    /** 내 주문 체결. */
    TRADE_EXECUTED,
    /** 공시 게시. */
    DISCLOSURE,
    /** 뉴스 게시. */
    NEWS,
    /** 리그 진행 (시작, 거래일 전환, 종료 등). */
    LEAGUE
}
