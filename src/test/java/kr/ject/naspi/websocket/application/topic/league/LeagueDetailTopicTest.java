package kr.ject.naspi.websocket.application.topic.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.StockListed;
import kr.ject.naspi.websocket.api.WebSocketConfig;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse.Player;
import kr.ject.naspi.websocket.api.response.league.LeagueDetailResponse.Stock;
import kr.ject.naspi.websocket.application.TopicMessagePublisher;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LeagueDetailTopicTest {

    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 11, 27, 20, 0);
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 11, 27, 21, 30, 1);

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final LeagueDetailMessageQuery query = mock(LeagueDetailMessageQuery.class);
    private final TopicMessagePublisher publisher = mock(TopicMessagePublisher.class);
    private final LeagueDetailTopic topic = new LeagueDetailTopic(subscriptions, query, publisher);
    private final LeagueQueryService leagueQueryService = mock(LeagueQueryService.class);
    private final LeagueDetailTopicAuthorizer authorizer =
            new LeagueDetailTopicAuthorizer(leagueQueryService);

    @Test
    @DisplayName("대기실 사용자는 다른 사용자의 상장 완료를 새로고침 없이 확인한다: 상장되면 그 리그 상세 구독자에게 발행한다")
    void publishOnStockListed() {
        subscribe(1L, "/topic/leagues/1/detail");
        given(query.getDetail(1L)).willReturn(response());

        topic.onStockListed(new StockListed(100L, 1L, 31L));

        verify(publisher).publish(message(1L));
    }

    @Test
    @DisplayName("리그 상세 구독자가 없는 리그는 조회하지 않는다 (리그 생성 중 방장 종목 상장 포함)")
    void noSubscribers() {
        subscribe(1L, "/topic/leagues/1/stocks");

        topic.onStockListed(new StockListed(100L, 1L, 30L));

        verifyNoInteractions(query, publisher);
    }

    @Test
    @DisplayName("조회가 실패해도(DB 오류, 커밋 직후 리그 삭제) 예외를 던지지 않고 발행하지 않는다")
    void failureIsSwallowed() {
        subscribe(1L, "/topic/leagues/1/detail");
        subscribe(2L, "/topic/leagues/2/detail");
        given(query.getDetail(1L)).willThrow(new QueryTimeoutException("DB 응답 없음"));
        given(query.getDetail(2L))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));

        assertThatCode(() -> {
            topic.onStockListed(new StockListed(100L, 1L, 31L));
            topic.onStockListed(new StockListed(101L, 2L, 41L));
        }).doesNotThrowAnyException();

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("상장 이벤트는 대기자 → 참여자 전환보다 먼저 발행되므로 커밋 이후에 받고, 웹소켓 발행 스레드 풀에서 비동기로 발행한다")
    void listenerRunsAfterCommitOnTopicPool() throws NoSuchMethodException {
        Method listener = LeagueDetailTopic.class.getMethod("onStockListed", StockListed.class);

        assertThat(listener.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(listener.getAnnotation(Async.class).value())
                .isEqualTo(LeagueDetailTopic.PUBLISH_EXECUTOR);
        Method pool = WebSocketConfig.class.getMethod(LeagueDetailTopic.PUBLISH_EXECUTOR);
        assertThat(pool.isAnnotationPresent(Bean.class)).isTrue();
        assertThat(Executor.class).isAssignableFrom(pool.getReturnType());
    }

    @Test
    @DisplayName("리그 상세 payload는 REST 리그 상세에서 플레이어의 me를 빼고 asOf를 더한 JSON이다")
    void serializesToAgreedJson() {
        JsonNode json = JsonMapper.builder().build().readTree(
                JsonMapper.builder().build().writeValueAsString(response()));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder("leagueId", "name",
                "inviteCode", "waitingRoomExpiresAt", "startable", "tradingDay", "players",
                "asOf");
        JsonNode player = json.get("players").get(1);
        assertThat(player.propertyNames()).containsExactlyInAnyOrder("playerId", "name", "host",
                "listed", "joinedAt", "listedAt", "stock");
        assertThat(player.get("listed").asBoolean()).isTrue();
        assertThat(player.get("stock").get("name").asString()).isEqualTo("민준건설");
        assertThat(json.get("tradingDay").isNull()).isTrue();
        assertThat(json.get("asOf").asString()).isEqualTo("2026-11-27T21:30:01");
    }

    @Test
    @DisplayName("리그에 입장한 회원(상장 전 대기자 포함)은 리그 상세를 구독할 수 있다")
    void joinedMemberCanSubscribe() {
        when(leagueQueryService.findAccessiblePlayerId(7L, 1L)).thenReturn(Optional.of(30L));

        assertThat(authorizer.canSubscribe(new LoginMember(7L), "/topic/leagues/1/detail"))
                .isTrue();
    }

    @Test
    @DisplayName("리그에 입장하지 않았거나, 리그가 없거나, 종료 시각이 지난 리그면 리그 상세를 구독할 수 없다")
    void inaccessibleCannotSubscribe() {
        when(leagueQueryService.findAccessiblePlayerId(7L, 2L)).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(new LoginMember(7L), "/topic/leagues/2/detail"))
                .isFalse();
    }

    @Test
    @DisplayName("리그 상세 권한은 리그 상세 경로만 담당한다")
    void supportsOnlyDetail() {
        assertThat(authorizer.supports("/topic/leagues/1/detail")).isTrue();
        assertThat(authorizer.supports("/topic/leagues/1/stocks")).isFalse();
        assertThat(authorizer.supports("/topic/leagues/1/detail/2")).isFalse();
    }

    private void subscribe(long leagueId, String... destinations) {
        given(subscriptions.subscribedDestinations("/topic/leagues/" + leagueId + "/detail"))
                .willReturn(Set.of(destinations));
    }

    static LeagueDetailResponse response() {
        return new LeagueDetailResponse(1L, "리그", "ABC1234", JOINED_AT.plusDays(7), false, null,
                List.of(new Player(30L, "수빈", true, true, JOINED_AT, JOINED_AT,
                                new Stock(99L, "수빈전자", "소개")),
                        new Player(31L, "민준", false, true, JOINED_AT.plusMinutes(1), AS_OF,
                                new Stock(100L, "민준건설", "소개2"))),
                AS_OF);
    }

    static TopicMessage message(long leagueId) {
        return new TopicMessage("/topic/leagues/" + leagueId + "/detail",
                LeagueDetailResponse.TYPE, response());
    }
}
