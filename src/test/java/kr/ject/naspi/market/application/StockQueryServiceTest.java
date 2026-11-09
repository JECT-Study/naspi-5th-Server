package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import(StockQueryService.class)
class StockQueryServiceTest {

    @Autowired
    private StockQueryService service;

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

    private long list(long leagueId, long listerPlayerId, String name) {
        Stock stock = Stock.list(leagueId, listerPlayerId, StockName.of(name),
                StockDescription.of("소개"));
        return entityManager.persistAndGetId(stock, Long.class);
    }
}
