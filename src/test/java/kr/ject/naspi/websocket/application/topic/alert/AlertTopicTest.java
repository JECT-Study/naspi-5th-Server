package kr.ject.naspi.websocket.application.topic.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.TradingDayChanged;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.websocket.api.response.alert.AlertResponse;
import kr.ject.naspi.websocket.api.response.alert.AlertType;
import kr.ject.naspi.websocket.application.PeriodicTopic;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.Async;

class AlertTopicTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 21, 30, 1);
    private static final long LEAGUE = 1L;
    private static final long STOCK = 100L;
    private static final long BUYER = 7L;
    private static final long SELLER = 8L;

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final StockQueryService stockQueryService = mock(StockQueryService.class);
    private final TopicMessagePublisher publisher = mock(TopicMessagePublisher.class);
    private final AlertTopic topic = new AlertTopic(subscriptions,
            new AlertMessageQuery(stockQueryService), publisher);
    private final LeagueQueryService leagueQueryService = mock(LeagueQueryService.class);
    private final AlertTopicAuthorizer authorizer = new AlertTopicAuthorizer(leagueQueryService);

    @Test
    @DisplayName("대기 중이던 매도 주문이 체결되면 매도자에게만 종목 이름과 매매 구분을 담은 체결 알림을 보낸다")
    void tradeExecutedToRestingSeller() {
        subscribe(LEAGUE, BUYER, SELLER);
        listStock();

        topic.onTradeExecuted(trade(false));

        verify(publisher).publish(message(SELLER, new AlertResponse(AlertType.TRADE_EXECUTED,
                "진영전자 매도 주문이 체결됐어요.", "/leagues/1/stocks/100")));
        verify(publisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("대기 중이던 매수 주문이 체결되면 매수자에게만 매수 체결 알림을 보내고 신규 주문자에게는 보내지 않는다")
    void tradeExecutedToRestingBuyer() {
        subscribe(LEAGUE, BUYER, SELLER);
        listStock();

        topic.onTradeExecuted(trade(true));

        verify(publisher).publish(message(BUYER, new AlertResponse(AlertType.TRADE_EXECUTED,
                "진영전자 매수 주문이 체결됐어요.", "/leagues/1/stocks/100")));
        verify(publisher, never()).publish(argThat(
                m -> m.destination().equals(AlertTopic.destination(LEAGUE, SELLER))));
    }

    @Test
    @DisplayName("대기 주문 주인이 구독하지 않았으면 종목을 조회하지도 발행하지도 않는다")
    void noPublishWhenRestingOwnerNotSubscribed() {
        subscribe(LEAGUE, BUYER);

        topic.onTradeExecuted(trade(false));

        verifyNoInteractions(stockQueryService, publisher);
    }

    @Test
    @DisplayName("거래일이 갱신되면 주문이 만료된 구독자에게는 주문 취소 알림을, 나머지 구독자에게는 거래일 갱신 알림을 한 번씩 보낸다")
    void tradingDayChangedPerSubscriber() {
        subscribe(LEAGUE, BUYER, SELLER, 9L);

        topic.onTradingDayChanged(new TradingDayChanged(LEAGUE, 2, Set.of(BUYER)));

        verify(publisher).publish(message(BUYER, new AlertResponse(AlertType.ORDER_EXPIRED,
                "새 거래일이 시작돼 대기 중인 주문이 취소됐어요.", null)));
        verify(publisher).publish(message(SELLER, new AlertResponse(
                AlertType.TRADING_DAY_CHANGED, "새 거래일이 시작됐어요.", null)));
        verify(publisher).publish(message(9L, new AlertResponse(
                AlertType.TRADING_DAY_CHANGED, "새 거래일이 시작됐어요.", null)));
        verify(publisher, times(3)).publish(any());
    }

    @Test
    @DisplayName("다른 리그의 구독자와 다른 토픽 구독자에게는 알림을 보내지 않는다")
    void onlyAlertSubscribersOfLeague() {
        given(subscriptions.subscribedDestinations("/topic/leagues/1/players/")).willReturn(Set.of(
                "/topic/leagues/1/players/7/alerts", "/topic/leagues/1/players/8/assets"));

        topic.onTradingDayChanged(new TradingDayChanged(LEAGUE, 2, Set.of()));

        verify(publisher).publish(message(BUYER, new AlertResponse(
                AlertType.TRADING_DAY_CHANGED, "새 거래일이 시작됐어요.", null)));
        verify(publisher, times(1)).publish(any());
    }

    @Test
    @DisplayName("체결 종목 조회가 실패하거나 리그에 종목이 없어도 예외를 던지지 않는다")
    void swallowFailures() {
        subscribe(LEAGUE, SELLER);
        given(stockQueryService.findAllByLeagueId(anyLong()))
                .willThrow(new QueryTimeoutException("timeout"))
                .willReturn(List.of());

        assertThatCode(() -> topic.onTradeExecuted(trade(false))).doesNotThrowAnyException();
        assertThatCode(() -> topic.onTradeExecuted(trade(false))).doesNotThrowAnyException();
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("알림은 주기 발행이 아니라 도메인 이벤트로 웹소켓 발행 스레드 풀에서 비동기로 보낸다")
    void eventDrivenAndAsync() throws NoSuchMethodException {
        List<Method> listeners = List.of(
                AlertTopic.class.getMethod("onTradeExecuted", TradeExecuted.class),
                AlertTopic.class.getMethod("onTradingDayChanged", TradingDayChanged.class));

        assertThat(PeriodicTopic.class.isAssignableFrom(AlertTopic.class)).isFalse();
        assertThat(listeners).allSatisfy(method -> assertThat(
                method.getAnnotation(Async.class).value()).isEqualTo(AlertTopic.PUBLISH_EXECUTOR));
    }

    @Test
    @DisplayName("알림은 로그인 회원 본인의 리그 플레이어 경로만 구독할 수 있다")
    void onlyOwnerCanSubscribe() {
        LoginMember member = new LoginMember(7L);
        when(leagueQueryService.findAccessiblePlayerId(7L, 1L)).thenReturn(Optional.of(30L));

        assertThat(authorizer.supports("/topic/leagues/1/players/30/alerts")).isTrue();
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/30/alerts")).isTrue();
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/31/alerts")).isFalse();
        // 플레이어 ID가 아니라 회원 ID를 넣으면 거부된다
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/7/alerts")).isFalse();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 알림 토픽을 구독할 수 없다")
    void outsiderCannotSubscribe() {
        LoginMember member = new LoginMember(7L);
        when(leagueQueryService.findAccessiblePlayerId(7L, 2L)).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(member, "/topic/leagues/2/players/30/alerts")).isFalse();
    }

    @Test
    @DisplayName("종료된 리그의 알림 토픽은 구독할 수 없다")
    void endedLeagueRejected() {
        LoginMember member = new LoginMember(7L);
        // 종료 시각이 지난 리그는 입장한 회원이어도 접근 가능한 플레이어가 없다
        when(leagueQueryService.findAccessiblePlayerId(7L, 3L)).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(member, "/topic/leagues/3/players/30/alerts")).isFalse();
    }

    static TradeExecuted trade(boolean buyResting) {
        return new TradeExecuted(LEAGUE, STOCK, 11L, 12L, BUYER, SELLER, 10_500, 3, NOW,
                buyResting);
    }

    static TopicMessage message(long playerId, AlertResponse alert) {
        return new TopicMessage(AlertTopic.destination(LEAGUE, playerId), AlertResponse.TYPE,
                alert);
    }

    private void listStock() {
        given(stockQueryService.findAllByLeagueId(LEAGUE)).willReturn(List.of(
                new LeagueStock(STOCK, "진영전자", "소개", 30L, 10_500L)));
    }

    private void subscribe(long leagueId, long... playerIds) {
        given(subscriptions.subscribedDestinations("/topic/leagues/" + leagueId + "/players/"))
                .willReturn(Arrays.stream(playerIds)
                        .mapToObj(id -> AlertTopic.destination(leagueId, id))
                        .collect(Collectors.toSet()));
    }
}
