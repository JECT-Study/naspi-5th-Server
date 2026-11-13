package kr.ject.naspi.market.application;

import java.util.Set;

/**
 * 현재가 변경 이벤트. 현재가 캐시 갱신에서 현재가가 바뀐 종목이 있는 리그를 알린다.
 *
 * <p>캐시를 바꾼 뒤 트랜잭션 밖에서 발행한다. 새로 캐시에 들어온 리그(리그 시작, 서버 재시작 직후)도 포함한다.
 * websocket은 이 이벤트로 해당 리그 구독자에게 자산을 발행한다 (docs/design/asset-realtime.md 2장).
 *
 * @param leagueIds 현재가가 바뀐 종목이 있는 리그 ID
 */
public record CurrentPricesChanged(Set<Long> leagueIds) {

    /**
     * 집합을 복사해 변경할 수 없게 만든다.
     */
    public CurrentPricesChanged {
        leagueIds = Set.copyOf(leagueIds);
    }
}
