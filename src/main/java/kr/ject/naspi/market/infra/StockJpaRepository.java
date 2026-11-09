package kr.ject.naspi.market.infra;

import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link StockRepository}의 Spring Data 구현.
 */
interface StockJpaRepository extends Repository<Stock, Long>, StockRepository {
}
