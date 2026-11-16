package kr.ject.naspi.league.application;

import java.util.List;

/**
 * 리그 시작 이벤트.
 *
 * <p>TODO: 발행하지 않는다. 초기 자산 배분은 {@link LeagueStartUseCase}가 직접 호출한다.
 * asset의 LeagueStartedListener가 아직 이 타입을 참조해서 남겨 두었고, asset 작업 때 함께 지운다.
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
