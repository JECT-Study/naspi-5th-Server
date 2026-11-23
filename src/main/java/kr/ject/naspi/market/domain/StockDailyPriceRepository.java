package kr.ject.naspi.market.domain;

import java.util.List;
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

    /**
     * 시세를 한 번에 저장한다.
     *
     * @param dailyPrices 종목 일별 시세
     * @return 저장된 시세 (ID 발급됨)
     */
    List<StockDailyPrice> saveAll(Iterable<StockDailyPrice> dailyPrices);

    /**
     * 리그의 거래일 시세를 모든 종목에 대해 조회한다.
     *
     * @param leagueId 리그 ID
     * @param tradingDay 거래일 번호
     * @return 거래일 시세 (리그당 최대 10행)
     */
    List<StockDailyPrice> findAllByLeagueIdAndTradingDay(long leagueId, int tradingDay);
}
