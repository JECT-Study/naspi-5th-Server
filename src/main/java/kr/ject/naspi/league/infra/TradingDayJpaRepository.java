package kr.ject.naspi.league.infra;

import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.league.domain.TradingDayRepository;
import org.springframework.data.repository.Repository;

/**
 * {@link TradingDayRepository}의 Spring Data 구현.
 */
interface TradingDayJpaRepository extends Repository<TradingDay, Long>, TradingDayRepository {
}
