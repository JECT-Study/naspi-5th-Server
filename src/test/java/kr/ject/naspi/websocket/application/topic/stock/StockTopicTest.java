package kr.ject.naspi.websocket.application.topic.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class StockTopicTest {

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final StockTopic topic = new StockTopic(subscriptions);
    private final StockTopicAuthorizer authorizer = new StockTopicAuthorizer();

    @Test
    @DisplayName("구독 중인 종목 정보 토픽에만 메시지를 만든다")
    void producesForSubscribedStockTopics() {
        when(subscriptions.subscribedDestinations("/topic/leagues/")).thenReturn(Set.of(
                "/topic/leagues/1/stocks",
                "/topic/leagues/1/stocks/3/candles"));

        assertThat(topic.produce()).containsExactly(
                new TopicMessage("/topic/leagues/1/stocks", "STOCK", StockTopic.DUMMY));
    }

    @Test
    @DisplayName("종목 정보는 stock 목록에 종목ID, 종목명, 현재가, 변동 가격, 등락률(%)을 담는다")
    void serializesToAgreedJson() {
        String json = JsonMapper.builder().build().writeValueAsString(StockTopic.DUMMY);

        assertThat(json).isEqualTo("{\"stock\":["
                + "{\"stockId\":1,\"stockName\":\"진영전자\",\"currentPrice\":12500,"
                + "\"changeAmount\":200,\"changeRate\":3.2},"
                + "{\"stockId\":2,\"stockName\":\"민영건설\",\"currentPrice\":12500,"
                + "\"changeAmount\":-10,\"changeRate\":-1.5}]}");
    }

    @Test
    @DisplayName("종목 정보는 로그인 회원 누구나 구독할 수 있다")
    void anyoneCanSubscribe() {
        assertThat(authorizer.supports("/topic/leagues/1/stocks")).isTrue();
        assertThat(authorizer.canSubscribe(new LoginMember(7L), "/topic/leagues/1/stocks"))
                .isTrue();
        assertThat(authorizer.supports("/topic/leagues/1/stocks/3/candles")).isFalse();
    }
}
