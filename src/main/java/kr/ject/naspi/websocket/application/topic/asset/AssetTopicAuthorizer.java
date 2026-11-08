package kr.ject.naspi.websocket.application.topic.asset;

import java.util.regex.Matcher;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import org.springframework.stereotype.Component;

/**
 * 내 자산 토픽 구독 권한. 경로의 {@code playerId}가 본인일 때만 구독할 수 있다.
 *
 * <p>TODO: 리그 모듈이 생기면 {@code playerId}가 로그인 회원의 해당 리그 참여자인지 확인.
 * 지금은 참여자가 없어 {@code playerId}를 회원 ID와 비교한다.
 */
@Component
public class AssetTopicAuthorizer implements TopicSubscriptionAuthorizer {

    @Override
    public boolean supports(String destination) {
        return AssetTopic.DESTINATION.matcher(destination).matches();
    }

    @Override
    public boolean canSubscribe(LoginMember member, String destination) {
        Matcher matcher = AssetTopic.DESTINATION.matcher(destination);
        return matcher.matches() && Long.parseLong(matcher.group(2)) == member.memberId();
    }
}
