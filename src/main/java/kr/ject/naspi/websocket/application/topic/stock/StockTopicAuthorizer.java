package kr.ject.naspi.websocket.application.topic.stock;

import java.util.regex.Matcher;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 종목 정보 토픽 구독 권한. 리그에 입장한 회원(상장 전 대기자 포함)만 구독할 수 있고, 리그가 없거나 종료 시각이
 * 지났으면 거절한다.
 */
@Component
@RequiredArgsConstructor
public class StockTopicAuthorizer implements TopicSubscriptionAuthorizer {

    private final LeagueQueryService leagueQueryService;

    @Override
    public boolean supports(String destination) {
        return StockTopic.DESTINATION.matcher(destination).matches();
    }

    @Override
    public boolean canSubscribe(LoginMember member, String destination) {
        Matcher matcher = StockTopic.DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return false;
        }
        long leagueId = Long.parseLong(matcher.group(1));
        return leagueQueryService.findAccessiblePlayerId(member.memberId(), leagueId).isPresent();
    }
}
