package kr.ject.naspi.websocket.application.topic.orderbook;

import java.util.List;
import java.util.regex.Pattern;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse.Level;
import kr.ject.naspi.websocket.application.PeriodicTopic;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 호가 토픽: {@code /topic/leagues/{leagueId}/stocks/{stockId}/orderbook}.
 *
 * <p>TODO: 실제 호가창(미체결 주문) 조회로 교체. 지금은 구독 중인 토픽에 더미 응답을 주기적으로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class OrderBookTopic implements PeriodicTopic {

    /** 구독 경로. 리그 참여자 누구나 구독. */
    static final Pattern DESTINATION =
            Pattern.compile("^/topic/leagues/(\\d+)/stocks/(\\d+)/orderbook$");

    /** 더미 응답. 100원 호가 단위로 매도·매수 5단계씩. */
    static final OrderBookResponse DUMMY = new OrderBookResponse(12_500,
            List.of(new Level(12_600, 30), new Level(12_700, 120), new Level(12_800, 45),
                    new Level(12_900, 80), new Level(13_000, 200)),
            List.of(new Level(12_500, 50), new Level(12_400, 90), new Level(12_300, 15),
                    new Level(12_200, 60), new Level(12_100, 150)));

    private static final String PREFIX = "/topic/leagues/";

    private final TopicSubscriptions subscriptions;

    @Override
    public String name() {
        return "orderbook";
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
                .map(destination -> new TopicMessage(destination, OrderBookResponse.TYPE, DUMMY))
                .toList();
    }
}
