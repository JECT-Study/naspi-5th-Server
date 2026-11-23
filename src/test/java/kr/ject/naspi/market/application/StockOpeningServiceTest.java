package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
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
@Import(StockOpeningService.class)
class StockOpeningServiceTest {

    private static final long LEAGUE_ID = 1L;

    @Autowired
    private StockOpeningService service;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("리그가 시작되면 1일차 기준가는 시초가 10,000원이고 상·하한가는 7,000~13,000원이다")
    void opensFirstDayWithOpeningPrice() {
        entityManager.persist(
                Stock.list(LEAGUE_ID, 10L, StockName.of("가나다"), StockDescription.of("소개")));
        entityManager.persist(
                Stock.list(LEAGUE_ID, 11L, StockName.of("라마바"), StockDescription.of("소개")));
        entityManager.flush();

        service.open(LEAGUE_ID);
        entityManager.flush();
        entityManager.clear();

        List<StockDailyPrice> rows = entityManager.getEntityManager()
                .createQuery("select d from StockDailyPrice d", StockDailyPrice.class)
                .getResultList();
        assertThat(rows).hasSize(2).allSatisfy(row -> {
            assertThat(row.getTradingDay()).isEqualTo(1);
            assertThat(row.getBasePrice()).isEqualTo(10_000);
            assertThat(row.getLowerLimitPrice()).isEqualTo(7_000);
            assertThat(row.getUpperLimitPrice()).isEqualTo(13_000);
            assertThat(row.getVolume()).isZero();
        });
    }
}
