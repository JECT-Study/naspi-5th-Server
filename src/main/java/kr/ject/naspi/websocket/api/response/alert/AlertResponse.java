package kr.ject.naspi.websocket.api.response.alert;

/**
 * 알림 메시지.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/members/{memberId}/alerts} (본인만 구독 가능)
 *
 * @param alertType 알림 종류
 * @param message 알림 내용
 * @param url 알림을 눌렀을 때 이동할 프론트 경로
 */
public record AlertResponse(AlertType alertType, String message, String url) {

    /** 메시지 종류 ({@code TopicEnvelope.type}). */
    public static final String TYPE = "ALERT";
}
