package kr.ject.naspi.market.domain;

import java.util.List;

/**
 * 종목 저장소. 구현은 infra의 {@code StockJpaRepository}가 맡는다.
 */
public interface StockRepository {

    /**
     * 종목을 저장한다.
     *
     * @param stock 종목
     * @return 저장된 종목 (ID 발급됨)
     */
    Stock save(Stock stock);

    /**
     * 같은 리그에 같은 이름의 종목이 있는지 확인한다.
     *
     * @param leagueId 리그 ID
     * @param name 종목 이름
     * @return 존재 여부
     */
    boolean existsByLeagueIdAndName(Long leagueId, StockName name);

    /**
     * 상장자가 이미 종목을 상장했는지 확인한다.
     *
     * @param listerPlayerId 상장자 플레이어 ID
     * @return 존재 여부
     */
    boolean existsByListerPlayerId(Long listerPlayerId);

    /**
     * 리그의 종목을 모두 조회한다.
     *
     * @param leagueId 리그 ID
     * @return 종목 목록
     */
    List<Stock> findAllByLeagueId(Long leagueId);
}
