package kr.ject.naspi.league.domain;

/**
 * 리그 결과 참여자 결과 저장소. 구현은 infra의 {@code LeagueResultPlayerJpaRepository}가 맡는다.
 */
public interface LeagueResultPlayerRepository {

    /**
     * 참여자 결과를 저장한다.
     *
     * @param player 참여자 결과
     * @return 저장된 참여자 결과 (ID 발급됨)
     */
    LeagueResultPlayer save(LeagueResultPlayer player);
}
