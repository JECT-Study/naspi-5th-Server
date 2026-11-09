package kr.ject.naspi.asset.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 현금 계좌 저장소. 구현은 infra의 {@code AccountJpaRepository}가 맡는다.
 */
public interface AccountRepository {

    /**
     * 계좌를 모두 저장한다.
     *
     * @param accounts 계좌 목록
     * @return 저장된 계좌 목록
     */
    <S extends Account> List<S> saveAll(Iterable<S> accounts);

    /**
     * 리그의 계좌를 모두 조회한다.
     *
     * @param leagueId 리그 ID
     * @return 계좌 목록
     */
    List<Account> findAllByLeagueId(Long leagueId);

    /**
     * 리그에 개설된 계좌가 있는지 확인한다. 초기 자산 배분 여부 판단에 쓴다.
     *
     * @param leagueId 리그 ID
     * @return 계좌 존재 여부
     */
    boolean existsByLeagueId(Long leagueId);

    /**
     * 리그 참여자의 계좌를 조회한다.
     *
     * @param leagueId 리그 ID
     * @param playerId 플레이어 ID
     * @return 계좌. 초기 자산이 배분되지 않았으면 비어 있다
     */
    Optional<Account> findByLeagueIdAndPlayerId(Long leagueId, Long playerId);

    /**
     * 리그 참여자 여러 명의 계좌를 한 번에 조회한다.
     *
     * @param leagueId 리그 ID
     * @param playerIds 플레이어 ID 목록
     * @return 계좌 목록. 초기 자산이 배분되지 않은 플레이어는 빠진다
     */
    List<Account> findAllByLeagueIdAndPlayerIdIn(Long leagueId, Collection<Long> playerIds);
}
