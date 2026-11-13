package kr.ject.naspi.asset.application;

import java.util.List;
import kr.ject.naspi.asset.domain.StockListing;
import kr.ject.naspi.league.application.LeagueStarted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 리그 시작 시 초기 자산을 배분한다.
 *
 * <p>리그 시작과 같은 트랜잭션에서 동기로 처리한다. 배분이 실패하면 리그 시작도 롤백되므로
 * 배분이 완료되지 않은 리그에서 거래가 열리지 않는다 (docs/policies/v1/domain/asset.md "초기 자산 배분").
 */
@Component
@RequiredArgsConstructor
class LeagueStartedListener {

    private final InitialAssetAllocationService initialAssetAllocationService;

    @EventListener
    void onLeagueStarted(LeagueStarted event) {
        List<StockListing> listings = event.listedStocks().stream()
                .map(stock -> new StockListing(stock.stockId(), stock.listerPlayerId()))
                .toList();
        initialAssetAllocationService.allocate(event.leagueId(), listings);
    }
}
