package kr.ject.naspi.market.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.PersistenceException;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDailyPriceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
class StockDailyPriceJpaRepositoryTest {

    @Autowired
    private StockDailyPriceRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("종목의 거래일 시세는 종목과 거래일로 조회한다")
    void findByStockIdAndTradingDay() {
        repository.save(StockDailyPrice.open(1L, 10L, 2, 10_000));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByStockIdAndTradingDay(10L, 2)).isPresent();
        assertThat(repository.findByStockIdAndTradingDay(10L, 1)).isEmpty();
        assertThat(repository.findByStockIdAndTradingDay(11L, 2)).isEmpty();
    }

    @Test
    @DisplayName("같은 종목의 같은 거래일 시세는 하나만 저장할 수 있다")
    void uniquePerStockAndDay() {
        repository.save(StockDailyPrice.open(1L, 10L, 1, 10_000));
        entityManager.flush();

        assertThatThrownBy(() -> {
            repository.save(StockDailyPrice.open(1L, 10L, 1, 10_000));
            entityManager.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class,
                PersistenceException.class);
    }

    @Test
    @DisplayName("리그의 거래일 시세는 모든 종목을 한 번에 조회한다")
    void findAllByLeagueIdAndTradingDay() {
        repository.saveAll(java.util.List.of(
                StockDailyPrice.open(1L, 10L, 1, 10_000),
                StockDailyPrice.open(1L, 11L, 1, 10_000),
                StockDailyPrice.open(1L, 10L, 2, 10_000),
                StockDailyPrice.open(2L, 20L, 1, 10_000)));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllByLeagueIdAndTradingDay(1L, 1)).hasSize(2);
        assertThat(repository.findAllByLeagueIdAndTradingDay(1L, 3)).isEmpty();
    }

    @Test
    @DisplayName("리그의 모든 거래일 시세를 조회하고 다른 리그는 제외한다")
    void findAllByLeagueId() {
        repository.saveAll(java.util.List.of(
                StockDailyPrice.open(1L, 10L, 1, 10_000),
                StockDailyPrice.open(1L, 10L, 2, 10_000),
                StockDailyPrice.open(1L, 11L, 1, 10_000),
                StockDailyPrice.open(2L, 20L, 1, 10_000)));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllByLeagueId(1L)).hasSize(3);
        assertThat(repository.findAllByLeagueId(3L)).isEmpty();
    }
}
