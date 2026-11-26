package kr.ject.naspi.websocket.application.topic.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.CurrentPricesChanged;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.websocket.api.WebSocketConfig;
import kr.ject.naspi.websocket.api.response.stock.StockResponse;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StockTopicTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 11, 27, 21, 30, 1);

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final StockMessageQuery query = mock(StockMessageQuery.class);
    private final TopicMessagePublisher publisher = mock(TopicMessagePublisher.class);
    private final StockTopic topic = new StockTopic(subscriptions, query, publisher);
    private final LeagueQueryService leagueQueryService = mock(LeagueQueryService.class);
    private final StockTopicAuthorizer authorizer = new StockTopicAuthorizer(leagueQueryService);

    @Test
    @DisplayName("토픽 메시지의 종목 항목은 종목 목록 REST 응답 항목에서 본인 종목 여부(mine)를 뺀 구조이고, 메시지에는 계산 시각(asOf)이 붙는다")
    void sameStructureAsRest() throws ClassNotFoundException {
        Class<?> rest = Class.forName("kr.ject.naspi.market.api.stock.MemberStockResponse");

        Set<String> expected = new HashSet<>(types(rest));
        assertThat(expected.remove("mine:boolean")).isTrue();
        assertThat(types(StockResponse.Stock.class)).isEqualTo(expected);
        assertThat(types(StockResponse.class))
                .containsExactlyInAnyOrder("stocks:List", "asOf:LocalDateTime");
    }

    @Test
    @DisplayName("종목 정보 payload는 stocks 배열과 asOf를 담고, 시세 아닌 필드도 매번 담는다")
    void serializesToAgreedJson() {
        JsonNode json = JsonMapper.builder().build().readTree(
                JsonMapper.builder().build().writeValueAsString(response()));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("stocks", "asOf");
        JsonNode item = json.get("stocks").get(0);
        assertThat(item.propertyNames()).doesNotContain("stockName", "stock", "mine");
        assertThat(item.get("name").asString()).isEqualTo("수빈전자");
        assertThat(item.get("description").asString()).isEqualTo("소개");
        assertThat(item.get("listerName").asString()).isEqualTo("수빈");
        assertThat(item.get("changeRate").decimalValue()).isEqualByComparingTo("25.0");
        assertThat(json.get("asOf").asString()).isEqualTo("2026-11-27T21:30:01");
    }

    @Test
    @DisplayName("현재가가 바뀐 리그의 종목 정보 구독자에게만 발행하고, 바뀌지 않은 리그에는 발행하지 않는다")
    void publishToChangedLeagueOnly() {
        subscribe(1L, "/topic/leagues/1/stocks");
        subscribe(2L, "/topic/leagues/2/stocks");
        given(query.getStocks(1L)).willReturn(response());

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L)));

        verify(query, never()).getStocks(2L);
        verify(publisher).publish(message(1L));
        verify(publisher, never()).publish(message(2L));
    }

    @Test
    @DisplayName("현재가가 바뀐 리그마다 한 번 조회해 리그 전체 종목을 하나의 메시지로 발행한다")
    void publishOncePerLeague() {
        subscribe(1L, "/topic/leagues/1/stocks");
        subscribe(2L, "/topic/leagues/2/stocks");
        given(query.getStocks(anyLong())).willReturn(response());

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L, 2L)));

        verify(query, times(1)).getStocks(1L);
        verify(query, times(1)).getStocks(2L);
        verify(publisher, times(1)).publish(message(1L));
        verify(publisher, times(1)).publish(message(2L));
    }

    @Test
    @DisplayName("종목 정보 구독자가 없는 리그는 조회하지 않는다")
    void noSubscribers() {
        subscribe(1L, "/topic/leagues/1/stocks/3/candles");

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L)));

        verifyNoInteractions(query, publisher);
    }

    @Test
    @DisplayName("체결되면 해당 리그의 종목 정보 구독자에게 발행해 같은 가격 체결의 거래량도 반영한다")
    void publishOnTradeExecuted() {
        subscribe(1L, "/topic/leagues/1/stocks");
        given(query.getStocks(1L)).willReturn(response());

        topic.onTradeExecuted(new TradeExecuted(1L, 100L, 11L, 12L, 7L, 8L, 10_500, 3, AS_OF));

        verify(publisher).publish(message(1L));
    }

    @Test
    @DisplayName("한 리그의 조회가 실패해도(DB 오류, 데이터 불일치) 예외를 던지지 않고 다른 리그 발행은 계속된다")
    void failureOfOneLeagueDoesNotBlockOthers() {
        subscribe(1L, "/topic/leagues/1/stocks");
        subscribe(2L, "/topic/leagues/2/stocks");
        subscribe(3L, "/topic/leagues/3/stocks");
        subscribe(4L, "/topic/leagues/4/stocks");
        given(query.getStocks(1L)).willThrow(new QueryTimeoutException("DB 응답 없음"));
        given(query.getStocks(2L)).willThrow(new NaspiRuntimeException("데이터 불일치"));
        given(query.getStocks(3L)).willThrow(new IllegalStateException("데이터 불일치"));
        given(query.getStocks(4L)).willReturn(response());

        assertThatCode(() -> topic.onCurrentPricesChanged(
                new CurrentPricesChanged(Set.of(1L, 2L, 3L, 4L)))).doesNotThrowAnyException();

        verify(publisher).publish(message(4L));
    }

    @Test
    @DisplayName("종목 정보 발행은 웹소켓 발행 스레드 풀에서 비동기로 한다")
    void listenersRunAsyncOnTopicPool() throws NoSuchMethodException {
        List<Method> listeners = List.of(
                StockTopic.class.getMethod("onCurrentPricesChanged", CurrentPricesChanged.class),
                StockTopic.class.getMethod("onTradeExecuted", TradeExecuted.class));

        assertThat(listeners).allSatisfy(method -> assertThat(
                method.getAnnotation(Async.class).value()).isEqualTo(StockTopic.PUBLISH_EXECUTOR));
        Method pool = WebSocketConfig.class.getMethod(StockTopic.PUBLISH_EXECUTOR);
        assertThat(pool.isAnnotationPresent(Bean.class)).isTrue();
        assertThat(Executor.class).isAssignableFrom(pool.getReturnType());
    }

    @Test
    @DisplayName("현재가 변경은 트랜잭션 밖에서 발행되므로 일반 리스너로, 체결은 커밋 이후 리스너로 받는다")
    void listenerPhases() throws NoSuchMethodException {
        Method priceChanged =
                StockTopic.class.getMethod("onCurrentPricesChanged", CurrentPricesChanged.class);
        Method traded = StockTopic.class.getMethod("onTradeExecuted", TradeExecuted.class);

        assertThat(priceChanged.isAnnotationPresent(EventListener.class)).isTrue();
        assertThat(priceChanged.isAnnotationPresent(TransactionalEventListener.class)).isFalse();
        assertThat(traded.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("리그에 입장한 회원(상장 전 대기자 포함)은 종목 정보를 구독할 수 있다")
    void participantCanSubscribe() {
        when(leagueQueryService.findPlayerId(7L, 1L)).thenReturn(Optional.of(30L));

        assertThat(authorizer.canSubscribe(new LoginMember(7L), "/topic/leagues/1/stocks"))
                .isTrue();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 종목 정보를 구독할 수 없다")
    void outsiderCannotSubscribe() {
        when(leagueQueryService.findPlayerId(eq(7L), eq(2L))).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(new LoginMember(7L), "/topic/leagues/2/stocks"))
                .isFalse();
    }

    @Test
    @DisplayName("종목 정보 권한은 종목 목록 경로만 담당한다")
    void supportsOnlyStockList() {
        assertThat(authorizer.supports("/topic/leagues/1/stocks")).isTrue();
        assertThat(authorizer.supports("/topic/leagues/1/stocks/3/candles")).isFalse();
        assertThat(authorizer.supports("/topic/leagues/1/stocks/3/orderbook")).isFalse();
    }

    private void subscribe(long leagueId, String... destinations) {
        given(subscriptions.subscribedDestinations("/topic/leagues/" + leagueId + "/stocks"))
                .willReturn(Set.of(destinations));
    }

    static StockResponse response() {
        return new StockResponse(List.of(
                new StockResponse.Stock(1L, "수빈전자", "소개", "수빈", 12_500L, 10_000L, 13_000L,
                        7_000L, 2_500L, new BigDecimal("25.0"), 5L)), AS_OF);
    }

    static TopicMessage message(long leagueId) {
        return new TopicMessage("/topic/leagues/" + leagueId + "/stocks", StockResponse.TYPE,
                response());
    }

    private static Set<String> types(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(component -> component.getName() + ":" + component.getType().getSimpleName())
                .collect(Collectors.toSet());
    }
}
