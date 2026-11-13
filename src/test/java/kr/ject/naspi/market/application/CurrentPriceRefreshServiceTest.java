package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.Set;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockSnapshot;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import kr.ject.naspi.member.application.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({CurrentPriceRefreshService.class, LocalStockSnapshotCache.class,
        LeagueQueryService.class, MemberService.class, ClockConfig.class})
class CurrentPriceRefreshServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 21, 0);

    @Autowired
    private CurrentPriceRefreshService service;

    @Autowired
    private LocalStockSnapshotCache cache;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("진행 중인 리그의 종목만 캐시에 담고, 새로 캐시에 들어온 리그는 현재가 변경으로 알린다")
    void cachesStartedLeaguesOnly() {
        long started = league("AAAAAAA", LeagueStatus.STARTED);
        long waiting = league("BBBBBBB", LeagueStatus.WAITING);
        final long stockId = stock(started, 10L, "가나다", 10_000L);
        stock(waiting, 20L, "라마바", null);

        Set<Long> changed = service.refresh();

        assertThat(changed).containsExactly(started);
        assertThat(cache.findAll()).containsOnlyKeys(started);
        assertThat(cache.findByLeagueId(started).orElseThrow())
                .extracting(StockSnapshot::stockId, StockSnapshot::currentPrice)
                .containsExactly(tuple(stockId, 10_000L));
        assertThat(events.stream(CurrentPricesChanged.class))
                .containsExactly(new CurrentPricesChanged(Set.of(started)));
    }

    @Test
    @DisplayName("현재가가 바뀐 종목이 있는 리그만 알리고, 바뀌지 않았으면 이벤트를 발행하지 않는다")
    void notifiesOnlyChangedLeagues() {
        long first = league("AAAAAAA", LeagueStatus.STARTED);
        long second = league("BBBBBBB", LeagueStatus.STARTED);
        final long firstStock = stock(first, 10L, "가나다", 10_000L);
        stock(second, 20L, "라마바", 10_000L);
        service.refresh();
        events.clear();

        assertThat(service.refresh()).isEmpty();
        assertThat(events.stream(CurrentPricesChanged.class)).isEmpty();

        changePrice(firstStock, 10_500L);

        assertThat(service.refresh()).containsExactly(first);
        assertThat(cache.findByLeagueId(first).orElseThrow())
                .extracting(StockSnapshot::currentPrice)
                .containsExactly(10_500L);
        assertThat(events.stream(CurrentPricesChanged.class))
                .containsExactly(new CurrentPricesChanged(Set.of(first)));
    }

    @Test
    @DisplayName("종료된 리그는 다음 갱신에서 캐시에서 빠진다")
    void evictsEndedLeagues() {
        long leagueId = league("AAAAAAA", LeagueStatus.STARTED);
        stock(leagueId, 10L, "가나다", 10_000L);
        service.refresh();

        League league = entityManager.find(League.class, leagueId);
        ReflectionTestUtils.setField(league, "status", LeagueStatus.ENDED);
        entityManager.flush();

        assertThat(service.refresh()).isEmpty();
        assertThat(cache.findByLeagueId(leagueId)).isEmpty();
    }

    private long league(String inviteCode, LeagueStatus status) {
        League league = League.create(LeagueName.of("리그"), InviteCode.of(inviteCode), NOW);
        ReflectionTestUtils.setField(league, "status", status);
        return entityManager.persistAndGetId(league, Long.class);
    }

    private long stock(long leagueId, long listerPlayerId, String name, Long currentPrice) {
        Stock stock = Stock.list(leagueId, listerPlayerId, StockName.of(name),
                StockDescription.of("소개"));
        ReflectionTestUtils.setField(stock, "currentPrice", currentPrice);
        return entityManager.persistAndGetId(stock, Long.class);
    }

    private void changePrice(long stockId, long price) {
        Stock stock = entityManager.find(Stock.class, stockId);
        ReflectionTestUtils.setField(stock, "currentPrice", price);
        entityManager.flush();
    }
}
