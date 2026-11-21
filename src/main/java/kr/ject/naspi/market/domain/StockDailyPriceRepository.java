package kr.ject.naspi.market.domain;

import java.util.Optional;

/**
 * 종목 일별 시세 저장소. 구현은 infra의 {@code StockDailyPriceJpaRepository}가 맡는다.
 */
public interface StockDailyPriceRepository {

    /**
     * 종목 일별 시세를 저장한다.
     *
     * @param dailyPrice 종목 일별 시세
     * @return 저장된 종목 일별 시세 (ID 발급됨)
     */
    StockDailyPrice save(StockDailyPrice dailyPrice);

    /**
     * 종목의 거래일 시세를 조회한다.
     *
     * @param stockId 종목 ID
     * @param tradingDay 거래일 번호
     * @return 거래일 시세, 없으면 빈 값
     */
    Optional<StockDailyPrice> findByStockIdAndTradingDay(long stockId, int tradingDay);
}
