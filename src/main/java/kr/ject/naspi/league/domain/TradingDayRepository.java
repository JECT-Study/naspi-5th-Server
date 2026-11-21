package kr.ject.naspi.league.domain;

import java.util.List;

/**
 * 거래일 저장소. 구현은 infra의 {@code TradingDayJpaRepository}가 맡는다.
 */
public interface TradingDayRepository {

    /**
     * 거래일을 한 번에 저장한다.
     *
     * @param tradingDays 거래일 목록
     * @return 저장된 거래일 (ID 발급됨)
     */
    List<TradingDay> saveAll(Iterable<TradingDay> tradingDays);

    /**
     * 리그의 거래일을 일차 순서대로 조회한다.
     *
     * @param leagueId 리그 ID
     * @return 거래일 (일차 순)
     */
    List<TradingDay> findAllByLeagueIdOrderByDayNumber(long leagueId);
}
