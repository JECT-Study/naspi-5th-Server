package kr.ject.naspi.websocket.application.topic.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlertTopicTest {

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final AlertTopic topic = new AlertTopic(subscriptions);
    private final LeagueQueryService leagueQueryService = mock(LeagueQueryService.class);
    private final AlertTopicAuthorizer authorizer = new AlertTopicAuthorizer(leagueQueryService);

    @Test
    @DisplayName("구독 중인 알림 토픽에만 메시지를 만든다")
    void producesForSubscribedAlertTopics() {
        when(subscriptions.subscribedDestinations("/topic/leagues/")).thenReturn(Set.of(
                "/topic/leagues/1/players/7/alerts",
                "/topic/leagues/1/players/7/assets"));

        assertThat(topic.produce()).containsExactly(new TopicMessage(
                "/topic/leagues/1/players/7/alerts", "ALERT", AlertTopic.DUMMY));
    }

    @Test
    @DisplayName("알림은 종류, 내용, 이동 경로를 담는다")
    void dummyHasAllFields() {
        assertThat(AlertTopic.DUMMY.alertType()).isNotNull();
        assertThat(AlertTopic.DUMMY.message()).isNotBlank();
        assertThat(AlertTopic.DUMMY.url()).startsWith("/");
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
}
