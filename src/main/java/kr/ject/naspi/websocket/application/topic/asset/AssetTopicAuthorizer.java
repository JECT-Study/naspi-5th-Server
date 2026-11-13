package kr.ject.naspi.websocket.application.topic.asset;

import java.util.regex.Matcher;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 내 자산 토픽 구독 권한. 경로의 {@code playerId}가 로그인 회원 본인의 해당 리그 플레이어일 때만 구독할 수 있다.
 *
 * <p>클라이언트가 보낸 {@code playerId}를 믿지 않고, 로그인 회원 ID와 경로의 {@code leagueId}로 league에서
 * 본인 플레이어를 찾아 비교한다. 리그에 입장하지 않은 회원(탈퇴 포함)은 구독할 수 없다.
 * 구독이 허용된 경로의 {@code playerId}만 발행 대상이 되므로, 발행할 때는 다시 확인하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AssetTopicAuthorizer implements TopicSubscriptionAuthorizer {

    private final LeagueQueryService leagueQueryService;

    @Override
    public boolean supports(String destination) {
        return AssetTopic.DESTINATION.matcher(destination).matches();
    }

    @Override
    public boolean canSubscribe(LoginMember member, String destination) {
        Matcher matcher = AssetTopic.DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return false;
        }
        long leagueId = Long.parseLong(matcher.group(1));
        long playerId = Long.parseLong(matcher.group(2));
        return leagueQueryService.findPlayerId(member.memberId(), leagueId)
                .map(ownPlayerId -> ownPlayerId == playerId)
                .orElse(false);
    }
}
