package kr.ject.naspi.websocket.application.topic.orderbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.CurrentPricesChanged;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.order.application.OrderBookQueryService;
import kr.ject.naspi.order.application.OrderBookView;
import kr.ject.naspi.order.application.OrderBookView.PriceLevel;
import kr.ject.naspi.order.application.OrderCanceled;
import kr.ject.naspi.order.application.OrderPlaced;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse;
import kr.ject.naspi.websocket.api.response.orderbook.OrderBookResponse.Level;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.annotation.Async;

class OrderBookTopicTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 27, 10, 0, 0);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Clock CLOCK = Clock.fixed(NOW.atZone(KST).toInstant(), KST);

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final OrderBookQueryService query = mock(OrderBookQueryService.class);
    private final TopicMessagePublisher publisher = mock(TopicMessagePublisher.class);
    private final OrderBookTopic topic = new OrderBookTopic(subscriptions, query, publisher, CLOCK);
    private final LeagueQueryService leagueQueryService = mock(LeagueQueryService.class);
    private final StockQueryService stockQueryService = mock(StockQueryService.class);
    private final OrderBookTopicAuthorizer authorizer =
            new OrderBookTopicAuthorizer(leagueQueryService, stockQueryService);

    @Test
    @DisplayName("토픽 메시지는 호가 조회 REST 응답의 필드에 계산 시각(asOf)만 더한 구조다")
    void sameStructureAsRest() throws ClassNotFoundException {
        Class<?> rest = Class.forName("kr.ject.naspi.order.api.orderbook.OrderBookResponse");
        Class<?> restLevel =
                Class.forName("kr.ject.naspi.order.api.orderbook.OrderBookResponse$Level");

        Set<String> expected = new HashSet<>(types(rest));
        expected.add("asOf:LocalDateTime");
        assertThat(types(OrderBookResponse.class)).isEqualTo(expected);
        assertThat(types(Level.class)).isEqualTo(types(restLevel));
    }

    @Test
    @DisplayName("체결되면 해당 종목 호가 구독 경로에 조회한 호가를 발행한다")
    void publishOrderBookOnTradeExecuted() {
        subscribe(1L, "/topic/leagues/1/stocks/3/orderbook");
        given(query.getOrderBook(1L, 3L)).willReturn(new OrderBookView(10_500,
                List.of(new PriceLevel(10_600, 30), new PriceLevel(10_700, 5)), List.of()));

        topic.onTradeExecuted(trade(1L, 3L));

        verify(publisher).publish(new TopicMessage("/topic/leagues/1/stocks/3/orderbook",
                OrderBookResponse.TYPE, new OrderBookResponse(10_500,
                        List.of(new Level(10_600, 30), new Level(10_700, 5)), List.of(), NOW)));
    }

    @Test
    @DisplayName("지정가 주문 접수·취소·만료되면 해당 종목 구독자에게 바뀐 호가를 발행한다")
    void publishOnOrderPlacedAndCanceled() {
        subscribe(1L, "/topic/leagues/1/stocks/3/orderbook");
        given(query.getOrderBook(1L, 3L)).willReturn(view());

        topic.onOrderPlaced(new OrderPlaced(1L, 3L, 11L, 7L));
        topic.onOrderCanceled(new OrderCanceled(1L, 3L, 11L, 7L, CancelReason.USER_REQUESTED));
        topic.onOrderCanceled(
                new OrderCanceled(1L, 3L, 12L, 7L, CancelReason.TRADING_DAY_EXPIRED));

        verify(publisher, times(3)).publish(message(1L, 3L));
    }

    @Test
    @DisplayName("다른 종목·다른 리그의 호가 구독자에게는 발행하지 않는다")
    void onlyTargetStockAndLeague() {
        subscribe(1L, "/topic/leagues/1/stocks/3/orderbook",
                "/topic/leagues/1/stocks/4/orderbook",
                "/topic/leagues/1/stocks/3/candles",
                "/topic/leagues/2/stocks/3/orderbook");
        given(query.getOrderBook(1L, 3L)).willReturn(view());

        topic.onTradeExecuted(trade(1L, 3L));

        verify(query).getOrderBook(1L, 3L);
        verify(query, times(1)).getOrderBook(anyLong(), anyLong());
        verify(publisher, times(1)).publish(message(1L, 3L));
    }

    @Test
    @DisplayName("구독자가 없는 종목은 호가를 조회하지 않는다")
    void noSubscribersNoQuery() {
        subscribe(1L, "/topic/leagues/1/stocks/4/orderbook");

        topic.onTradeExecuted(trade(1L, 3L));

        verify(query, never()).getOrderBook(anyLong(), anyLong());
        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("현재가가 바뀐 리그의 구독 중인 종목 호가를 모두 다시 발행하고, 바뀌지 않은 리그는 조회하지 않는다")
    void currentPricesChangedRepublishesSubscribedStocks() {
        subscribe(1L, "/topic/leagues/1/stocks/3/orderbook",
                "/topic/leagues/1/stocks/4/orderbook");
        subscribe(2L, "/topic/leagues/2/stocks/9/orderbook");
        given(query.getOrderBook(1L, 3L)).willReturn(view());
        given(query.getOrderBook(1L, 4L)).willReturn(view());

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L)));

        verify(publisher).publish(message(1L, 3L));
        verify(publisher).publish(message(1L, 4L));
        verify(query, never()).getOrderBook(2L, 9L);
    }

    @Test
    @DisplayName("리그 종료 일괄 취소 후처럼 거래할 수 없는 리그면 예외 없이 발행을 건너뛴다")
    void endedLeagueIsSkipped() {
        subscribe(1L, "/topic/leagues/1/stocks/3/orderbook");
        given(query.getOrderBook(1L, 3L))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_ENDED));

        assertThatCode(() -> topic.onTradeExecuted(trade(1L, 3L))).doesNotThrowAnyException();
        // 리그 종료 일괄 취소는 조회 자체를 하지 않는다
        topic.onOrderCanceled(new OrderCanceled(1L, 3L, 11L, 7L, CancelReason.LEAGUE_ENDED));

        verify(query, times(1)).getOrderBook(1L, 3L);
        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("한 종목의 조회가 실패해도(DB 오류·데이터 불일치) 다른 종목·리그 발행은 계속된다")
    void failureIsolatedPerStock() {
        subscribe(1L, "/topic/leagues/1/stocks/3/orderbook",
                "/topic/leagues/1/stocks/4/orderbook", "/topic/leagues/1/stocks/5/orderbook",
                "/topic/leagues/1/stocks/6/orderbook");
        subscribe(2L, "/topic/leagues/2/stocks/9/orderbook");
        given(query.getOrderBook(1L, 3L))
                .willThrow(new DataAccessResourceFailureException("DB 응답 없음"));
        given(query.getOrderBook(1L, 4L)).willThrow(new IllegalStateException("현재가 없음"));
        given(query.getOrderBook(1L, 5L))
                .willThrow(new NaspiBizException(ErrorCode.STOCK_NOT_FOUND));
        given(query.getOrderBook(1L, 6L)).willReturn(view());
        given(query.getOrderBook(2L, 9L)).willReturn(view());

        assertThatCode(() -> topic.onCurrentPricesChanged(
                new CurrentPricesChanged(Set.of(1L, 2L)))).doesNotThrowAnyException();

        verify(publisher).publish(message(1L, 6L));
        verify(publisher).publish(message(2L, 9L));
    }

    @Test
    @DisplayName("발행은 이벤트를 낸 스레드를 막지 않도록 웹소켓 발행 스레드 풀에서 비동기로 한다")
    void listenersRunAsyncOnTopicPool() throws NoSuchMethodException {
        List<Method> listeners = List.of(
                OrderBookTopic.class.getMethod("onCurrentPricesChanged",
                        CurrentPricesChanged.class),
                OrderBookTopic.class.getMethod("onTradeExecuted", TradeExecuted.class),
                OrderBookTopic.class.getMethod("onOrderPlaced", OrderPlaced.class),
                OrderBookTopic.class.getMethod("onOrderCanceled", OrderCanceled.class));

        assertThat(listeners).allSatisfy(method -> assertThat(
                method.getAnnotation(Async.class).value())
                .isEqualTo(OrderBookTopic.PUBLISH_EXECUTOR));
    }

    @Test
    @DisplayName("진행 중인 리그의 참여자는 호가 토픽을 구독할 수 있다")
    void participantOfTradableLeagueCanSubscribe() {
        String destination = "/topic/leagues/1/stocks/3/orderbook";
        given(leagueQueryService.findPlayerId(7L, 1L)).willReturn(Optional.of(30L));
        given(stockQueryService.findAllByLeagueId(1L)).willReturn(List.of(stock(3L)));

        assertThat(authorizer.supports(destination)).isTrue();
        assertThat(authorizer.canSubscribe(new LoginMember(7L), destination)).isTrue();
        assertThat(authorizer.supports("/topic/leagues/1/stocks/3/candles")).isFalse();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 호가 토픽을 구독할 수 없다")
    void outsiderCannotSubscribe() {
        given(leagueQueryService.findPlayerId(7L, 1L)).willReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(new LoginMember(7L),
                "/topic/leagues/1/stocks/3/orderbook")).isFalse();
        verify(leagueQueryService, never()).requireTradable(anyLong());
    }

    @Test
    @DisplayName("시작 전·종료된·없는 리그의 호가 토픽은 참여자여도 구독할 수 없다")
    void notTradableLeagueCannotSubscribe() {
        given(leagueQueryService.findPlayerId(7L, 1L)).willReturn(Optional.of(30L));
        given(stockQueryService.findAllByLeagueId(1L)).willReturn(List.of(stock(3L)));

        for (ErrorCode code : List.of(ErrorCode.LEAGUE_NOT_TRADABLE,
                ErrorCode.LEAGUE_ALREADY_ENDED, ErrorCode.LEAGUE_NOT_FOUND)) {
            doThrow(new NaspiBizException(code)).when(leagueQueryService).requireTradable(1L);

            assertThat(authorizer.canSubscribe(new LoginMember(7L),
                    "/topic/leagues/1/stocks/3/orderbook")).isFalse();
        }
    }

    @Test
    @DisplayName("리그에 없는 종목의 호가 토픽은 구독할 수 없다")
    void stockNotInLeagueCannotSubscribe() {
        given(leagueQueryService.findPlayerId(7L, 1L)).willReturn(Optional.of(30L));
        given(stockQueryService.findAllByLeagueId(1L)).willReturn(List.of(stock(3L)));

        assertThat(authorizer.canSubscribe(new LoginMember(7L),
                "/topic/leagues/1/stocks/99/orderbook")).isFalse();
    }

    private void subscribe(long leagueId, String... destinations) {
        given(subscriptions.subscribedDestinations("/topic/leagues/" + leagueId + "/stocks/"))
                .willReturn(Set.of(destinations));
    }

    private static LeagueStock stock(long stockId) {
        return new LeagueStock(stockId, "종목", "소개", 30L, 10_000L);
    }

    private static TradeExecuted trade(long leagueId, long stockId) {
        return new TradeExecuted(leagueId, stockId, 11L, 12L, 7L, 8L, 10_500, 3, NOW);
    }

    private static OrderBookView view() {
        return new OrderBookView(10_000, List.of(), List.of());
    }

    private static TopicMessage message(long leagueId, long stockId) {
        return new TopicMessage(
                "/topic/leagues/" + leagueId + "/stocks/" + stockId + "/orderbook",
                OrderBookResponse.TYPE, new OrderBookResponse(10_000, List.of(), List.of(), NOW));
    }

    private static Set<String> types(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(component -> component.getName() + ":" + component.getType().getSimpleName())
                .collect(Collectors.toSet());
    }
}
