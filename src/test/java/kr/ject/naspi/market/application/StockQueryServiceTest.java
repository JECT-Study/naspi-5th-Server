package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockSnapshot;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({StockQueryService.class, LocalStockSnapshotCache.class})
class StockQueryServiceTest {

    @Autowired
    private StockQueryService service;

    @Autowired
    private LocalStockSnapshotCache cache;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("리그의 종목만 이름, 상장자, 현재가와 함께 종목 ID 순으로 조회한다")
    void findAllByLeagueId() {
        long first = list(1L, 10L, "가나다");
        long second = list(1L, 11L, "라마바");
        list(2L, 20L, "가나다");

        assertThat(service.findAllByLeagueId(1L))
                .extracting(LeagueStock::stockId, LeagueStock::name,
                        LeagueStock::listerPlayerId, LeagueStock::currentPrice)
                .containsExactly(
                        tuple(first, "가나다", 10L, null),
                        tuple(second, "라마바", 11L, null));
    }

    @Test
    @DisplayName("현재가 캐시에 있는 리그(진행 중)는 DB가 아니라 캐시에서 조회한다")
    void findAllByLeagueIdFromCache() {
        long stockId = list(1L, 10L, "가나다");
        cache.replaceAll(Map.of(1L, List.of(
                new StockSnapshot(stockId, 1L, "가나다", "소개", 10L, 10_500L))));

        assertThat(service.findAllByLeagueId(1L))
                .extracting(LeagueStock::stockId, LeagueStock::currentPrice)
                .containsExactly(tuple(stockId, 10_500L));
    }

    private long list(long leagueId, long listerPlayerId, String name) {
        Stock stock = Stock.list(leagueId, listerPlayerId, StockName.of(name),
                StockDescription.of("소개"));
        return entityManager.persistAndGetId(stock, Long.class);
    }
}
