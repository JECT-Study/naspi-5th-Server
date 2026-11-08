package kr.ject.naspi.league.application;

import java.util.List;

/**
 * 리그 시작 이벤트.
 *
 * <p>TODO: 목(mock) 이벤트. 리그 시작 기능(LEA-09)을 구현할 때 실제 발행 코드와 함께 확정한다.
 * 지금은 발행하는 곳이 없고, asset의 초기 자산 배분 리스너를 연결해 두기 위해서만 존재한다.
 * 상장 종목 목록은 market 모듈이 생기면 이벤트에서 빼고 구독자가 market application으로 조회하도록 바꾼다.
 *
 * @param leagueId 시작한 리그 ID
 * @param listedStocks 리그의 모든 상장 종목과 상장자
 */
public record LeagueStarted(long leagueId, List<ListedStock> listedStocks) {

    /**
     * 상장 종목.
     *
     * @param stockId 종목 ID
     * @param listerPlayerId 상장자 플레이어 ID
     */
    public record ListedStock(long stockId, long listerPlayerId) {
    }
}
