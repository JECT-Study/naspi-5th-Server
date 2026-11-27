package kr.ject.naspi.order.infra;

import java.util.Collection;
import java.util.List;
import kr.ject.naspi.order.domain.OrderExecutionTotal;
import kr.ject.naspi.order.domain.Trade;
import kr.ject.naspi.order.domain.TradeRepository;
import kr.ject.naspi.order.domain.TradeTotal;
import org.springframework.data.domain.Limit;
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

    @Override
    default List<Trade> findRecentByStockId(long stockId, int limit) {
        return findByStockIdOrderByExecutedAtDescIdDesc(stockId, Limit.of(limit));
    }

    /**
     * 종목의 체결을 체결 일시·ID 역순으로 최대 {@code limit}건 조회한다.
     *
     * @param stockId 종목 ID
     * @param limit 최대 건수
     * @return 체결 기록
     */
    List<Trade> findByStockIdOrderByExecutedAtDescIdDesc(long stockId, Limit limit);

    @Override
    @Query("""
            select new kr.ject.naspi.order.domain.OrderExecutionTotal(
                t.buyOrderId, sum(t.quantity), sum(t.amount), min(t.price), max(t.price))
            from Trade t
            where t.buyOrderId in :ids
            group by t.buyOrderId
            """)
    List<OrderExecutionTotal> sumByBuyOrderIds(@Param("ids") Collection<Long> buyOrderIds);

    @Override
    @Query("""
            select new kr.ject.naspi.order.domain.OrderExecutionTotal(
                t.sellOrderId, sum(t.quantity), sum(t.amount), min(t.price), max(t.price))
            from Trade t
            where t.sellOrderId in :ids
            group by t.sellOrderId
            """)
    List<OrderExecutionTotal> sumBySellOrderIds(@Param("ids") Collection<Long> sellOrderIds);
}
