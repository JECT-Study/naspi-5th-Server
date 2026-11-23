package kr.ject.naspi.market.infra;

import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDailyPriceRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link StockDailyPriceRepository}의 Spring Data 구현.
 */
interface StockDailyPriceJpaRepository
        extends Repository<StockDailyPrice, Long>, StockDailyPriceRepository {
}
