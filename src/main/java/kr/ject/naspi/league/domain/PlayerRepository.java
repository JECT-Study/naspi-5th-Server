package kr.ject.naspi.league.domain;

import java.util.Optional;

/**
 * 플레이어 저장소. 구현은 infra의 {@code PlayerJpaRepository}가 맡는다.
 */
public interface PlayerRepository {

    /**
     * 플레이어를 저장한다.
     *
     * @param player 플레이어
     * @return 저장된 플레이어 (ID 발급됨)
     */
    Player save(Player player);

    /**
     * 플레이어 ID로 플레이어를 찾는다.
     *
     * @param id 플레이어 ID
     * @return 플레이어, 없으면 빈 값
     */
    Optional<Player> findById(Long id);

    /**
     * 회원이 이미 리그에 입장했는지 확인한다.
     *
     * @param leagueId 리그 ID
     * @param memberId 회원 ID
     * @return 입장 여부
     */
    boolean existsByLeagueIdAndMemberId(Long leagueId, Long memberId);
}
