package kr.ject.naspi.asset.domain;

import java.util.List;

/**
 * 보유 주식 저장소. 구현은 infra의 {@code HoldingJpaRepository}가 맡는다.
 */
public interface HoldingRepository {

    /**
     * 보유 주식을 모두 저장한다.
     *
     * @param holdings 보유 주식 목록
     * @return 저장된 보유 주식 목록
     */
    <S extends Holding> List<S> saveAll(Iterable<S> holdings);

    /**
     * 리그의 보유 주식을 모두 조회한다.
     *
     * @param leagueId 리그 ID
     * @return 보유 주식 목록
     */
    List<Holding> findAllByLeagueId(Long leagueId);
}
