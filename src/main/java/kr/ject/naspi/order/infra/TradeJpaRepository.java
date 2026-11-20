package kr.ject.naspi.order.infra;

import kr.ject.naspi.order.domain.Trade;
import kr.ject.naspi.order.domain.TradeRepository;
import kr.ject.naspi.order.domain.TradeTotal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * {@link TradeRepository}의 Spring Data 구현.
 */
interface TradeJpaRepository extends Repository<Trade, Long>, TradeRepository {

    @Override
    @Query("""
            select new kr.ject.naspi.order.domain.TradeTotal(
                coalesce(sum(t.quantity), 0L), coalesce(sum(t.amount), 0L))
            from Trade t
            where t.stockId = :stockId and t.tradingDay = :tradingDay
            """)
    TradeTotal sumByStockIdAndTradingDay(@Param("stockId") long stockId,
            @Param("tradingDay") int tradingDay);
}
