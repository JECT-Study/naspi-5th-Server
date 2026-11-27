package kr.ject.naspi.league.domain;

import java.util.List;

/**
 * 리그 결과 종목별 보유 저장소. 구현은 infra의 {@code LeagueResultHoldingJpaRepository}가 맡는다.
 */
public interface LeagueResultHoldingRepository {

    /**
     * 종목별 보유를 한 번에 저장한다.
     *
     * @param holdings 종목별 보유 목록
     * @param <S> 종목별 보유 타입
     * @return 저장된 종목별 보유
     */
    <S extends LeagueResultHolding> List<S> saveAll(Iterable<S> holdings);
}
