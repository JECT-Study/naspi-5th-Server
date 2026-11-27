package kr.ject.naspi.league.domain;

import java.util.List;

/**
 * 리그 결과 종목 평가 저장소. 구현은 infra의 {@code LeagueResultStockJpaRepository}가 맡는다.
 */
public interface LeagueResultStockRepository {

    /**
     * 종목 평가를 한 번에 저장한다.
     *
     * @param stocks 종목 평가 목록
     * @param <S> 종목 평가 타입
     * @return 저장된 종목 평가
     */
    <S extends LeagueResultStock> List<S> saveAll(Iterable<S> stocks);
}
