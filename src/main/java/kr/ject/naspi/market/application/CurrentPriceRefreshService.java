package kr.ject.naspi.market.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockRepository;
import kr.ject.naspi.market.domain.StockSnapshot;
import kr.ject.naspi.market.domain.StockSnapshotCache;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 현재가 캐시 갱신 (docs/design/asset-realtime.md 3장).
 *
 * <p>진행 중인 리그의 종목을 DB에서 한 번에 읽어 캐시를 통째로 덮어쓴다. 캐시에 쓰는 곳은 여기 하나뿐이다.
 * 이전 캐시와 비교해 현재가가 바뀐 종목이 있는 리그를 {@link CurrentPricesChanged}로 알린다.
 * 종료된 리그는 다음 갱신에서 캐시에서 빠진다.
 */
@Service
@RequiredArgsConstructor
public class CurrentPriceRefreshService {

    private final LeagueQueryService leagueQueryService;
    private final StockRepository stockRepository;
    private final StockSnapshotCache cache;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 캐시를 DB 값으로 덮어쓰고, 현재가가 바뀐 리그가 있으면 이벤트를 발행한다.
     *
     * @return 현재가가 바뀐 종목이 있는 리그 ID
     */
    public Set<Long> refresh() {
        Map<Long, List<StockSnapshot>> latest = loadStartedLeagues();
        Map<Long, List<StockSnapshot>> previous = cache.findAll();
        cache.replaceAll(latest);

        Set<Long> changed = latest.entrySet().stream()
                .filter(entry -> !Objects.equals(prices(previous.get(entry.getKey())),
                        prices(entry.getValue())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
        if (!changed.isEmpty()) {
            eventPublisher.publishEvent(new CurrentPricesChanged(changed));
        }
        return changed;
    }

    private Map<Long, List<StockSnapshot>> loadStartedLeagues() {
        List<Long> leagueIds = leagueQueryService.findStartedLeagueIds();
        if (leagueIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<StockSnapshot>> result = new HashMap<>();
        leagueIds.forEach(leagueId -> result.put(leagueId, List.of()));
        stockRepository.findAllByLeagueIdIn(leagueIds).stream()
                .sorted(Comparator.comparing(Stock::getId))
                .map(StockSnapshot::from)
                .collect(Collectors.groupingBy(StockSnapshot::leagueId))
                .forEach(result::put);
        return result;
    }

    /** 종목 ID별 현재가. 캐시에 없던 리그는 {@code null}이라 새로 들어온 리그는 바뀐 것으로 본다. */
    private static Map<Long, Long> prices(List<StockSnapshot> snapshots) {
        if (snapshots == null) {
            return null;
        }
        Map<Long, Long> prices = new HashMap<>();
        snapshots.forEach(snapshot -> prices.put(snapshot.stockId(), snapshot.currentPrice()));
        return prices;
    }
}
