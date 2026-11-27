package kr.ject.naspi.league.domain;

import java.util.List;

/**
 * 거래일별 자산 집계 저장소. 구현은 infra의 {@code DailyAssetSnapshotJpaRepository}가 맡는다.
 */
public interface DailyAssetSnapshotRepository {

    /**
     * 거래일별 자산 집계를 저장한다.
     *
     * @param snapshots 거래일별 자산 집계
     * @param <S> 거래일별 자산 집계 타입
     * @return 저장된 거래일별 자산 집계 (ID 발급됨)
     */
    <S extends DailyAssetSnapshot> List<S> saveAll(Iterable<S> snapshots);

    /**
     * 리그의 거래일 자산 집계가 이미 있는지 확인한다.
     *
     * @param leagueId 리그 ID
     * @param tradingDay 거래일 번호
     * @return 집계가 있으면 true
     */
    boolean existsByLeagueIdAndTradingDay(long leagueId, int tradingDay);

    /**
     * 리그의 한 거래일 자산 집계를 모두 조회한다.
     *
     * @param leagueId 리그 ID
     * @param tradingDay 거래일 번호
     * @return 참여자별 거래일 자산 집계 (탈퇴자 포함)
     */
    List<DailyAssetSnapshot> findAllByLeagueIdAndTradingDay(long leagueId, int tradingDay);
}
