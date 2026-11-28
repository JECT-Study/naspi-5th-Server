package kr.ject.naspi.websocket.application.topic.orderbook;

import java.util.regex.Matcher;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.websocket.application.TopicSubscriptionAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 호가 토픽 구독 권한. 호가 조회 REST와 같은 조건으로, 리그에 입장한 회원이 진행 중인 리그의 종목 경로를
 * 구독할 때만 허용한다.
 *
 * <p>리그에 입장하지 않은 회원, 시작 전·종료된·없는 리그, 리그에 없는 종목은 거절한다.
 */
@Component
@RequiredArgsConstructor
public class OrderBookTopicAuthorizer implements TopicSubscriptionAuthorizer {

    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;

    @Override
    public boolean supports(String destination) {
        return OrderBookTopic.DESTINATION.matcher(destination).matches();
    }

    @Override
    public boolean canSubscribe(LoginMember member, String destination) {
        Matcher matcher = OrderBookTopic.DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return false;
        }
        long leagueId = Long.parseLong(matcher.group(1));
        long stockId = Long.parseLong(matcher.group(2));
        if (leagueQueryService.findPlayerId(member.memberId(), leagueId).isEmpty()) {
            return false;
        }
        try {
            leagueQueryService.requireTradable(leagueId);
        } catch (NaspiBizException e) {
            return false;
        }
        return stockQueryService.findAllByLeagueId(leagueId).stream()
                .anyMatch(stock -> stock.stockId() == stockId);
    }
}
