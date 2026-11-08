package kr.ject.naspi.websocket.application.topic.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import kr.ject.naspi.websocket.api.response.stock.StockResponse;
import kr.ject.naspi.websocket.api.response.stock.StockResponse.Stock;
import kr.ject.naspi.websocket.application.PeriodicTopic;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 종목 정보 토픽: {@code /topic/leagues/{leagueId}/stocks}.
 *
 * <p>TODO: 실제 시세(현재가·기준가) 조회로 교체. 지금은 구독 중인 토픽에 더미 응답을 주기적으로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class StockTopic implements PeriodicTopic {

    /** 구독 경로. 리그 참여자 누구나 구독. */
    static final Pattern DESTINATION = Pattern.compile("^/topic/leagues/(\\d+)/stocks$");

    /** 더미 응답. */
    static final StockResponse DUMMY = new StockResponse(List.of(
            new Stock(1, "진영전자", 12_500, 200, new BigDecimal("3.2")),
            new Stock(2, "민영건설", 12_500, -10, new BigDecimal("-1.5"))));

    private static final String PREFIX = "/topic/leagues/";

    private final TopicSubscriptions subscriptions;

    @Override
    public String name() {
        return "stocks";
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
                .map(destination -> new TopicMessage(destination, StockResponse.TYPE, DUMMY))
                .toList();
    }
}
