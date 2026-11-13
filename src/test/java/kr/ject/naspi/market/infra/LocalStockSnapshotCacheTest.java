package kr.ject.naspi.market.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.market.domain.StockSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalStockSnapshotCacheTest {

    private final LocalStockSnapshotCache cache = new LocalStockSnapshotCache();

    @Test
    @DisplayName("갱신 전에는 비어 있고, 통째로 바꾸면 넘기지 않은 리그는 빠진다")
    void replaceAll() {
        assertThat(cache.findByLeagueId(1L)).isEmpty();

        cache.replaceAll(Map.of(1L, List.of(snapshot(1L, 10_000L)),
                2L, List.of(snapshot(2L, 10_000L))));
        cache.replaceAll(Map.of(2L, List.of(snapshot(2L, 10_500L))));

        assertThat(cache.findByLeagueId(1L)).isEmpty();
        assertThat(cache.findByLeagueId(2L).orElseThrow())
                .extracting(StockSnapshot::currentPrice)
                .containsExactly(10_500L);
    }

    @Test
    @DisplayName("넘긴 맵이나 목록을 나중에 바꿔도 캐시 값은 바뀌지 않는다")
    void copiesInput() {
        List<StockSnapshot> snapshots = new ArrayList<>(List.of(snapshot(1L, 10_000L)));
        Map<Long, List<StockSnapshot>> input = new HashMap<>(Map.of(1L, snapshots));

        cache.replaceAll(input);
        snapshots.add(snapshot(1L, 20_000L));
        input.put(2L, List.of());

        assertThat(cache.findAll()).containsOnlyKeys(1L);
        assertThat(cache.findByLeagueId(1L).orElseThrow()).hasSize(1);
    }

    private static StockSnapshot snapshot(long leagueId, long price) {
        return new StockSnapshot(leagueId * 100, leagueId, "가나다", "소개", 10L, price);
    }
}
