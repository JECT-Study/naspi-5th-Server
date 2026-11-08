package kr.ject.naspi.asset.domain;

/**
 * 초기 자산 배분 대상 종목과 그 상장자.
 *
 * <p>참여자는 1인 1종목을 상장하므로 상장자 목록이 곧 배분 대상 참여자 목록이다.
 *
 * @param stockId 종목 ID
 * @param listerPlayerId 상장자 플레이어 ID
 */
public record StockListing(long stockId, long listerPlayerId) {
}
