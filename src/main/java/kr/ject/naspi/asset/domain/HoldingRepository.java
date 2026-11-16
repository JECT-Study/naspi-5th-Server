package kr.ject.naspi.asset.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /**
     * 리그 참여자의 보유 주식을 모두 조회한다.
     *
     * @param leagueId 리그 ID
     * @param playerId 플레이어 ID
     * @return 보유 주식 목록
     */
    List<Holding> findAllByLeagueIdAndPlayerId(Long leagueId, Long playerId);

    /**
     * 리그 참여자 여러 명의 보유 주식을 한 번에 조회한다.
     *
     * @param leagueId 리그 ID
     * @param playerIds 플레이어 ID 목록
     * @return 보유 주식 목록
     */
    List<Holding> findAllByLeagueIdAndPlayerIdIn(Long leagueId, Collection<Long> playerIds);

    /**
     * 리그 참여자의 종목 보유 주식을 쓰기 락을 걸어 조회한다. 예약·정산처럼 수량을 확인하고 바꾸는 작업에 쓴다.
     *
     * @param leagueId 리그 ID
     * @param playerId 플레이어 ID
     * @param stockId 종목 ID
     * @return 보유 주식. 보유한 적이 없으면 비어 있다
     */
    Optional<Holding> findWithLockByLeagueIdAndPlayerIdAndStockId(Long leagueId, Long playerId,
            Long stockId);
}
