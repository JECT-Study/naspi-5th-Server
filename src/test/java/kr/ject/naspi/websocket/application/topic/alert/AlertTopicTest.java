package kr.ject.naspi.websocket.application.topic.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.websocket.domain.TopicMessage;
import kr.ject.naspi.websocket.infra.TopicSubscriptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlertTopicTest {

    private final TopicSubscriptions subscriptions = mock(TopicSubscriptions.class);
    private final AlertTopic topic = new AlertTopic(subscriptions);
    private final AlertTopicAuthorizer authorizer = new AlertTopicAuthorizer();

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
    @DisplayName("알림은 본인만 구독할 수 있다")
    void onlyOwnerCanSubscribe() {
        LoginMember member = new LoginMember(7L);

        assertThat(authorizer.supports("/topic/leagues/1/players/7/alerts")).isTrue();
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/7/alerts")).isTrue();
        assertThat(authorizer.canSubscribe(member, "/topic/leagues/1/players/8/alerts")).isFalse();
    }
}
