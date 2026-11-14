package kr.ject.naspi.market.infra;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import kr.ject.naspi.market.domain.StockSnapshot;
import kr.ject.naspi.market.domain.StockSnapshotCache;
import org.springframework.stereotype.Component;

/**
 * {@link StockSnapshotCache}의 로컬 메모리 구현. 단일 서버 전제다.
 *
 * <p>변경할 수 없는 맵을 통째로 바꿔 끼우므로, 조회하는 쪽은 락 없이 항상 한 시점의 완전한 값을 본다.
 * 다중 서버로 가면 Redis 구현으로 교체한다.
 */
@Component
public class LocalStockSnapshotCache implements StockSnapshotCache {

    private volatile Map<Long, List<StockSnapshot>> snapshots = Map.of();

    @Override
    public Optional<List<StockSnapshot>> findByLeagueId(long leagueId) {
        return Optional.ofNullable(snapshots.get(leagueId));
    }

    @Override
    public Map<Long, List<StockSnapshot>> findAll() {
        return snapshots;
    }

    @Override
    public void replaceAll(Map<Long, List<StockSnapshot>> snapshotsByLeagueId) {
        snapshots = snapshotsByLeagueId.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())));
    }
}
