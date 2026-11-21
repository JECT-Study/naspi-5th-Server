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
        repository.save(StockDailyPrice.start(1L, 10L, 2));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByStockIdAndTradingDay(10L, 2)).isPresent();
        assertThat(repository.findByStockIdAndTradingDay(10L, 1)).isEmpty();
        assertThat(repository.findByStockIdAndTradingDay(11L, 2)).isEmpty();
    }

    @Test
    @DisplayName("같은 종목의 같은 거래일 시세는 하나만 저장할 수 있다")
    void uniquePerStockAndDay() {
        repository.save(StockDailyPrice.start(1L, 10L, 1));
        entityManager.flush();

        assertThatThrownBy(() -> {
            repository.save(StockDailyPrice.start(1L, 10L, 1));
            entityManager.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class,
                PersistenceException.class);
    }
}
