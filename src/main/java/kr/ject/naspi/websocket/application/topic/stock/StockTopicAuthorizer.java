package kr.ject.naspi.websocket.application.topic.stock;

import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import org.springframework.stereotype.Component;

/**
 * 종목 정보 토픽 구독 권한. 로그인 회원 누구나 구독할 수 있다.
 *
 * <p>TODO: 리그 모듈이 생기면 리그 참여자인지 확인.
 */
@Component
public class StockTopicAuthorizer implements TopicSubscriptionAuthorizer {

    @Override
    public boolean supports(String destination) {
        return StockTopic.DESTINATION.matcher(destination).matches();
    }

    @Override
    public boolean canSubscribe(LoginMember member, String destination) {
        return true;
    }
}
