package kr.ject.naspi.websocket.application.topic.alert;

import java.util.regex.Matcher;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import org.springframework.stereotype.Component;

/**
 * 알림 토픽 구독 권한. 경로의 {@code memberId}가 본인일 때만 구독할 수 있다.
 *
 * <p>TODO: 리그 모듈이 생기면 리그 참여자인지 확인.
 */
@Component
public class AlertTopicAuthorizer implements TopicSubscriptionAuthorizer {

    @Override
    public boolean supports(String destination) {
        return AlertTopic.DESTINATION.matcher(destination).matches();
    }

    @Override
    public boolean canSubscribe(LoginMember member, String destination) {
        Matcher matcher = AlertTopic.DESTINATION.matcher(destination);
        return matcher.matches() && Long.parseLong(matcher.group(2)) == member.memberId();
    }
}
