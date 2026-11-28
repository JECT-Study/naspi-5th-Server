package kr.ject.naspi.league.domain;

/**
 * 리그 결과 저장소. 구현은 infra의 {@code LeagueResultJpaRepository}가 맡는다.
 */
public interface LeagueResultRepository {

    /**
     * 리그 결과를 저장한다.
     *
     * @param leagueResult 리그 결과
     * @return 저장된 리그 결과 (ID 발급됨)
     */
    LeagueResult save(LeagueResult leagueResult);

    /**
     * 리그 결과가 이미 있는지 확인한다.
     *
     * @param leagueId 리그 ID
     * @return 결과가 있으면 true
     */
    boolean existsByLeagueId(Long leagueId);
}
