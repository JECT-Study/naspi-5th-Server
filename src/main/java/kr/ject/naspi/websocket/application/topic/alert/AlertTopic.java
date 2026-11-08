package kr.ject.naspi.websocket.application.topic.alert;

import java.util.List;
import java.util.regex.Pattern;
import kr.ject.naspi.websocket.api.response.alert.AlertResponse;
import kr.ject.naspi.websocket.api.response.alert.AlertType;
import kr.ject.naspi.websocket.application.PeriodicTopic;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 알림 토픽: {@code /topic/leagues/{leagueId}/players/{playerId}/alerts}.
 *
 * <p>TODO: 체결·공시 등 도메인 이벤트를 받아 보내도록 교체. 지금은 구독 중인 토픽에 더미 응답을 주기적으로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class AlertTopic implements PeriodicTopic {

    /** 구독 경로. {@code playerId}가 본인일 때만 구독. */
    static final Pattern DESTINATION =
            Pattern.compile("^/topic/leagues/(\\d+)/players/(\\d+)/alerts$");

    /** 더미 응답. */
    static final AlertResponse DUMMY = new AlertResponse(AlertType.TRADE_EXECUTED,
            "진영전자 10주가 12,500원에 체결되었습니다.", "/leagues/1/stocks/1");

    private static final String PREFIX = "/topic/leagues/";

    private final TopicSubscriptions subscriptions;

    @Override
    public String name() {
        return "alerts";
    }

    @Override
    public String destinationPrefix() {
        return PREFIX;
    }

    @Override
    public List<TopicMessage> produce() {
        return subscriptions.subscribedDestinations(PREFIX).stream()
                .filter(destination -> DESTINATION.matcher(destination).matches())
                .sorted()
                .map(destination -> new TopicMessage(destination, AlertResponse.TYPE, DUMMY))
                .toList();
    }
}
