package kr.ject.naspi.websocket.application.topic.league;

import java.util.regex.Matcher;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 리그 상세 토픽 구독 권한. REST 리그 상세처럼 리그에 입장한 회원(상장 전 대기자 포함)만 구독할 수 있고, 리그가
 * 없거나 종료 시각이 지났으면 거절한다.
 */
@Component
@RequiredArgsConstructor
public class LeagueDetailTopicAuthorizer implements TopicSubscriptionAuthorizer {

    private final LeagueQueryService leagueQueryService;

    @Override
    public boolean supports(String destination) {
        return LeagueDetailTopic.DESTINATION.matcher(destination).matches();
    }

    @Override
    public boolean canSubscribe(LoginMember member, String destination) {
        Matcher matcher = LeagueDetailTopic.DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return false;
        }
        long leagueId = Long.parseLong(matcher.group(1));
        return leagueQueryService.findAccessiblePlayerId(member.memberId(), leagueId).isPresent();
    }
}
