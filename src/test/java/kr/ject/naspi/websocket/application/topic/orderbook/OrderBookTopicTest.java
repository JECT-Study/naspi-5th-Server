package kr.ject.naspi.websocket.application.topic.orderbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Comparator;
import java.util.Set;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse.Level;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderBookTopicTest {

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final OrderBookTopic topic = new OrderBookTopic(subscriptions);
    private final OrderBookTopicAuthorizer authorizer = new OrderBookTopicAuthorizer();

    @Test
    @DisplayName("구독 중인 호가 토픽에만 메시지를 만든다")
    void producesForSubscribedOrderBookTopics() {
        when(subscriptions.subscribedDestinations("/topic/leagues/")).thenReturn(Set.of(
                "/topic/leagues/1/stocks/3/orderbook",
                "/topic/leagues/1/stocks/3/candles"));

        assertThat(topic.produce()).containsExactly(new TopicMessage(
                "/topic/leagues/1/stocks/3/orderbook", "ORDER_BOOK", OrderBookTopic.DUMMY));
    }

    @Test
    @DisplayName("호가는 100원 단위이고, 매도는 낮은 가격부터, 매수는 높은 가격부터 정렬된다")
    void dummyLevelsAreOrderedOnTickUnit() {
        OrderBookResponse orderBook = OrderBookTopic.DUMMY;

        assertThat(orderBook.asks())
                .isSortedAccordingTo(Comparator.comparingLong(Level::orderPrice));
        assertThat(orderBook.bids())
                .isSortedAccordingTo(Comparator.comparingLong(Level::orderPrice).reversed());
        assertThat(orderBook.asks().getFirst().orderPrice())
                .isGreaterThan(orderBook.bids().getFirst().orderPrice());
        assertThat(orderBook.asks()).extracting(Level::orderPrice)
                .allSatisfy(price -> assertThat(price % 100).isZero());
        assertThat(orderBook.bids()).extracting(Level::orderPrice)
                .allSatisfy(price -> assertThat(price % 100).isZero());
    }

    @Test
    @DisplayName("호가는 로그인 회원 누구나 구독할 수 있다")
    void anyoneCanSubscribe() {
        String destination = "/topic/leagues/1/stocks/3/orderbook";

        assertThat(authorizer.supports(destination)).isTrue();
        assertThat(authorizer.canSubscribe(new LoginMember(7L), destination)).isTrue();
        assertThat(authorizer.supports("/topic/leagues/1/stocks/3/candles")).isFalse();
    }
}
