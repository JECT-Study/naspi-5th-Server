package kr.ject.naspi.market.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 진행 중인 리그의 종목 정보(현재가 포함) 캐시. 화면 표시용 조회에만 쓴다.
 *
 * <p>원본은 DB이고, 캐시에 쓰는 곳은 주기 갱신({@code CurrentPriceRefreshService}) 하나뿐이다.
 * 그래서 늦게 도착한 옛날 값이 최신 값을 덮어쓰는 경쟁이 없다. 캐시는 최대 갱신 주기만큼 DB보다 늦을 수 있다.
 * 주문·체결·정산 같은 거래 로직은 이 캐시를 읽지 않는다 (docs/design/asset-realtime.md 3장).
 *
 * <p>구현은 infra가 맡는다. 지금은 단일 서버 전제의 로컬 메모리 구현이다.
 */
public interface StockSnapshotCache {

    /**
     * 리그의 종목 정보를 조회한다.
     *
     * @param leagueId 리그 ID
     * @return 종목 ID 순 종목 정보. 캐시에 없는 리그(대기실, 종료, 갱신 전)는 빈 값
     */
    Optional<List<StockSnapshot>> findByLeagueId(long leagueId);

    /**
     * 캐시에 있는 모든 리그의 종목 정보를 조회한다.
     *
     * @return 리그 ID별 종목 정보
     */
    Map<Long, List<StockSnapshot>> findAll();

    /**
     * 캐시 전체를 바꾼다. 넘기지 않은 리그는 캐시에서 빠진다.
     *
     * @param snapshotsByLeagueId 리그 ID별 종목 정보
     */
    void replaceAll(Map<Long, List<StockSnapshot>> snapshotsByLeagueId);
}
