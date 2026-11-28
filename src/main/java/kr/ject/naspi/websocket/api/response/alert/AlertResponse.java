package kr.ject.naspi.websocket.api.response.alert;

/**
 * 알림 메시지.
 *
 * <p>토픽: {@code /topic/leagues/{leagueId}/players/{playerId}/alerts} (본인만 구독 가능)
 *
 * @param alertType 알림 종류
 * @param message 알림 내용
 * @param url 알림을 눌렀을 때 이동할 프론트 경로. 이동할 곳이 없으면 null
 * @param newsType 뉴스 알림의 원문 종류({@code DISCLOSURE}, {@code REPORT}). 뉴스 알림이 아니면 null
 * @param newsId 뉴스 알림의 뉴스 ID. 뉴스 알림이 아니면 null
 */
public record AlertResponse(AlertType alertType, String message, String url, String newsType,
                            Long newsId) {

    /** 메시지 종류 ({@code TopicEnvelope.type}). */
    public static final String TYPE = "ALERT";
}
