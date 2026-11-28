package kr.ject.naspi.websocket.application.topic.asset;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import kr.ject.naspi.asset.application.AssetQueryService;
import kr.ject.naspi.asset.application.PlayerAsset;
import kr.ject.naspi.asset.application.PlayerAsset.HoldingAsset;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse;
import kr.ject.naspi.websocket.api.response.asset.AssetResponse.Holding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 내 자산 메시지 조회. REST와 같은 자산 조회({@link AssetQueryService})에 계산 시각({@code asOf})을 붙인다.
 */
@Component
@RequiredArgsConstructor
class AssetMessageQuery {

    private final AssetQueryService assetQueryService;
    private final Clock clock;

    /**
     * 같은 리그 플레이어들의 내 자산 메시지를 만든다. 계산 시각은 조회 직전 시각이다.
     *
     * @param leagueId 리그 ID
     * @param playerIds 구독할 때 본인 확인을 마친 플레이어 ID
     * @return 자산이 있는 플레이어의 메시지. 플레이어 ID 순. 자산이 없는 플레이어(리그 시작 전)는 빠진다
     */
    public List<AssetResponse> getAssets(long leagueId, Collection<Long> playerIds) {
        LocalDateTime asOf = LocalDateTime.now(clock);
        return assetQueryService.getPlayerAssets(leagueId, playerIds).stream()
                .map(asset -> toResponse(asset, asOf))
                .toList();
    }

    private static AssetResponse toResponse(PlayerAsset asset, LocalDateTime asOf) {
        return new AssetResponse(asset.playerId(), asset.deposit(), asset.orderableDeposit(),
                asset.stockValuation(), asset.totalAsset(), asset.totalProfit(),
                asset.totalReturnRate(),
                asset.holdings().stream().map(AssetMessageQuery::toHolding).toList(), asOf);
    }

    private static Holding toHolding(HoldingAsset holding) {
        return new Holding(holding.stockId(), holding.stockName(), holding.treasuryStock(),
                holding.quantity(), holding.orderableQuantity(), holding.currentPrice(),
                holding.averagePrice(), holding.valuation(), holding.profit(),
                holding.returnRate());
    }
}
