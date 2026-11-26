package kr.ject.naspi.websocket.application.topic.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import kr.ject.naspi.order.application.OrderCanceled;
import kr.ject.naspi.order.application.OrderPlaced;
import kr.ject.naspi.order.application.TradeExecuted;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.websocket.api.WebSocketConfig;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.Async;

class AssetTopicTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 11, 25, 21, 30, 1);

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final AssetMessageQuery query = mock(AssetMessageQuery.class);
    private final TopicMessagePublisher publisher = mock(TopicMessagePublisher.class);
    private final AssetTopic topic = new AssetTopic(subscriptions, query, publisher);
    private final LeagueQueryService leagueQueryService = mock(LeagueQueryService.class);
    private final AssetTopicAuthorizer authorizer = new AssetTopicAuthorizer(leagueQueryService);

    @Test
    @DisplayName("현재가가 바뀐 리그의 내 자산 구독자 전원에게 리그당 한 번 조회해 발행한다")
    void publishToSubscribersOfChangedLeague() {
        subscribe(1L, "/topic/leagues/1/players/7/assets", "/topic/leagues/1/players/8/assets",
                "/topic/leagues/1/players/7/alerts");
        given(query.getAssets(1L, List.of(7L, 8L))).willReturn(List.of(asset(7L), asset(8L)));

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L)));

        verify(query, times(1)).getAssets(anyLong(), anyCollection());
        verify(publisher).publish(message(1L, 7L));
        verify(publisher).publish(message(1L, 8L));
    }

    @Test
    @DisplayName("현재가가 바뀌지 않은 리그의 구독자는 조회하지도 발행하지도 않는다")
    void skipUnchangedLeague() {
        subscribe(1L, "/topic/leagues/1/players/7/assets");
        subscribe(2L, "/topic/leagues/2/players/9/assets");
        given(query.getAssets(1L, List.of(7L))).willReturn(List.of(asset(7L)));

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L)));

        verify(query, never()).getAssets(eq(2L), anyCollection());
        verify(publisher).publish(message(1L, 7L));
        verify(publisher, never()).publish(message(2L, 9L));
    }

    @Test
    @DisplayName("자산이 없는 플레이어(리그 시작 전)는 발행하지 않는다")
    void skipPlayerWithoutAsset() {
        subscribe(1L, "/topic/leagues/1/players/7/assets", "/topic/leagues/1/players/8/assets");
        given(query.getAssets(1L, List.of(7L, 8L))).willReturn(List.of(asset(8L)));

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L)));

        verify(publisher).publish(message(1L, 8L));
        verify(publisher, never()).publish(message(1L, 7L));
    }

    @Test
    @DisplayName("구독자가 없는 리그는 자산을 조회하지 않는다")
    void noSubscribers() {
        subscribe(1L);

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L)));

        verifyNoInteractions(query, publisher);
    }

    @Test
    @DisplayName("한 리그의 조회가 실패해도 다른 리그의 발행은 계속된다")
    void failureOfOneLeagueDoesNotBlockOthers() {
        subscribe(1L, "/topic/leagues/1/players/7/assets");
        subscribe(2L, "/topic/leagues/2/players/9/assets");
        given(query.getAssets(1L, List.of(7L))).willThrow(new IllegalStateException("현재가 없음"));
        given(query.getAssets(2L, List.of(9L))).willReturn(List.of(asset(9L)));

        topic.onCurrentPricesChanged(new CurrentPricesChanged(Set.of(1L, 2L)));

        verify(publisher).publish(message(2L, 9L));
    }

    @Test
    @DisplayName("DB 조회 실패나 자산 계산 불가(데이터 불일치)여도 예외를 던지지 않고 다른 리그 발행은 계속된다")
    void dbAndDataFailuresAreIsolated() {
        subscribe(1L, "/topic/leagues/1/players/7/assets");
        subscribe(2L, "/topic/leagues/2/players/8/assets");
        subscribe(3L, "/topic/leagues/3/players/9/assets");
        given(query.getAssets(1L, List.of(7L)))
                .willThrow(new QueryTimeoutException("DB 응답 없음"));
        given(query.getAssets(2L, List.of(8L)))
                .willThrow(new NaspiRuntimeException("현재가가 정해지지 않았습니다"));
        given(query.getAssets(3L, List.of(9L))).willReturn(List.of(asset(9L)));

        assertThatCode(() -> topic.onCurrentPricesChanged(
                new CurrentPricesChanged(Set.of(1L, 2L, 3L)))).doesNotThrowAnyException();

        verify(publisher).publish(message(3L, 9L));
    }

    @Test
    @DisplayName("체결되면 매수자·매도자 중 구독 중인 플레이어에게만 발행한다")
    void publishToBuyerAndSeller() {
        subscribe(1L, "/topic/leagues/1/players/7/assets", "/topic/leagues/1/players/8/assets",
                "/topic/leagues/1/players/9/assets");
        given(query.getAssets(1L, List.of(7L, 8L))).willReturn(List.of(asset(7L), asset(8L)));

        topic.onTradeExecuted(new TradeExecuted(1L, 100L, 11L, 12L, 7L, 8L, 10_500, 3, AS_OF));

        verify(query).getAssets(1L, List.of(7L, 8L));
        verify(publisher).publish(message(1L, 7L));
        verify(publisher).publish(message(1L, 8L));
        verify(publisher, never()).publish(message(1L, 9L));
    }

    @Test
    @DisplayName("주문을 접수·취소하면 주문한 플레이어에게만 발행하고, 구독하지 않았으면 조회하지 않는다")
    void publishToOrderingPlayer() {
        subscribe(1L, "/topic/leagues/1/players/7/assets", "/topic/leagues/1/players/8/assets");
        given(query.getAssets(1L, List.of(7L))).willReturn(List.of(asset(7L)));

        topic.onOrderPlaced(new OrderPlaced(1L, 100L, 11L, 7L));
        topic.onOrderCanceled(new OrderCanceled(1L, 100L, 11L, 7L, CancelReason.USER_REQUESTED));
        topic.onOrderPlaced(new OrderPlaced(1L, 100L, 12L, 99L));

        verify(publisher, times(2)).publish(message(1L, 7L));
        verify(query, times(2)).getAssets(anyLong(), anyCollection());
        verify(publisher, never()).publish(message(1L, 8L));
    }

    @Test
    @DisplayName("발행은 이벤트를 낸 스레드를 막지 않도록 웹소켓 발행 스레드 풀에서 비동기로 한다")
    void listenersRunAsyncOnTopicPool() throws NoSuchMethodException {
        List<Method> listeners = List.of(
                AssetTopic.class.getMethod("onCurrentPricesChanged", CurrentPricesChanged.class),
                AssetTopic.class.getMethod("onTradeExecuted", TradeExecuted.class),
                AssetTopic.class.getMethod("onOrderPlaced", OrderPlaced.class),
                AssetTopic.class.getMethod("onOrderCanceled", OrderCanceled.class));

        assertThat(listeners).allSatisfy(method -> assertThat(
                method.getAnnotation(Async.class).value()).isEqualTo(AssetTopic.PUBLISH_EXECUTOR));
        Method pool = WebSocketConfig.class.getMethod(AssetTopic.PUBLISH_EXECUTOR);
        assertThat(pool.isAnnotationPresent(Bean.class)).isTrue();
        assertThat(Executor.class).isAssignableFrom(pool.getReturnType());
    }

    @Test
    @DisplayName("토픽 메시지는 내 자산 REST 응답의 필드에 계산 시각(asOf)만 더한 구조다")
    void sameStructureAsRest() throws ClassNotFoundException {
        Class<?> rest = Class.forName("kr.ject.naspi.asset.api.me.MyAssetResponse");
        Class<?> restHolding = Class.forName("kr.ject.naspi.asset.api.me.MyAssetResponse$Holding");

        Set<String> expected = new HashSet<>(types(rest));
        expected.add("asOf:LocalDateTime");
        assertThat(types(AssetResponse.class)).isEqualTo(expected);
        assertThat(types(AssetResponse.Holding.class)).isEqualTo(types(restHolding));
    }

    @Test
    @DisplayName("내 자산은 로그인 회원 본인의 리그 플레이어 경로만 구독할 수 있다")
    void onlyOwnerCanSubscribe() {
        LoginMember member = new LoginMember(7L);
        when(leagueQueryService.findAccessiblePlayerId(7L, 1L)).thenReturn(Optional.of(30L));

        assertThat(authorizer.supports("/topic/leagues/1/players/30/assets")).isTrue();
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/30/assets")).isTrue();
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/31/assets")).isFalse();
        // 플레이어 ID가 아니라 회원 ID를 넣으면 거부된다
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/7/assets")).isFalse();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 내 자산 토픽을 구독할 수 없다")
    void outsiderCannotSubscribe() {
        LoginMember member = new LoginMember(7L);
        when(leagueQueryService.findAccessiblePlayerId(7L, 2L)).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(member, "/topic/leagues/2/players/30/assets")).isFalse();
    }

    @Test
    @DisplayName("종료된 리그의 자산 토픽은 구독할 수 없다")
    void endedLeagueRejected() {
        LoginMember member = new LoginMember(7L);
        // 종료 시각이 지난 리그는 입장한 회원이어도 접근 가능한 플레이어가 없다
        when(leagueQueryService.findAccessiblePlayerId(7L, 3L)).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(member, "/topic/leagues/3/players/30/assets")).isFalse();
    }

    private void subscribe(long leagueId, String... destinations) {
        given(subscriptions.subscribedDestinations("/topic/leagues/" + leagueId + "/players/"))
                .willReturn(Set.of(destinations));
    }

    static AssetResponse asset(long playerId) {
        return new AssetResponse(playerId, 50_000_000, 50_000_000, 50_000_000, 100_000_000, 0,
                new BigDecimal("0.0"), List.of(), AS_OF);
    }

    static TopicMessage message(long leagueId, long playerId) {
        return new TopicMessage("/topic/leagues/" + leagueId + "/players/" + playerId + "/assets",
                AssetResponse.TYPE, asset(playerId));
    }

    private static Set<String> types(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(component -> component.getName() + ":" + component.getType().getSimpleName())
                .collect(Collectors.toSet());
    }
}
