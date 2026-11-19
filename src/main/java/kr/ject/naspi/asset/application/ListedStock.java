package kr.ject.naspi.asset.application;

/**
 * 초기 자산 배분 대상 종목과 그 상장자. 다른 모듈이 {@link InitialAssetAllocationService#allocate}에 넘기는 입력이다.
 *
 * @param stockId 종목 ID
 * @param listerPlayerId 상장자 플레이어 ID
 */
public record ListedStock(long stockId, long listerPlayerId) {
}
