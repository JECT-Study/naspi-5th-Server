package kr.ject.naspi.market.application;

import kr.ject.naspi.league.application.LeagueCreated;
import kr.ject.naspi.league.application.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 리그가 생성되면 방장의 종목을 상장하고 방장을 참여자로 바꾼다.
 *
 * <p>리그 생성과 같은 트랜잭션에서 동기로 처리한다. 상장 규칙에 걸리면 예외가 리그 생성까지 전파되어
 * 리그도 롤백되므로, 방장의 상장 없이 리그만 남는 상태가 생기지 않는다
 * (docs/policies/v1/domain/league.md "리그 생성 및 초대").
 */
@Component
@RequiredArgsConstructor
class LeagueCreatedListener {

    private final StockListingService stockListingService;
    private final LeagueService leagueService;

    @EventListener
    void onLeagueCreated(LeagueCreated event) {
        stockListingService.list(event.leagueId(), event.hostPlayerId(),
                event.stockName(), event.stockDescription());
        leagueService.completeListing(event.hostPlayerId());
    }
}
