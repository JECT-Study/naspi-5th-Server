package kr.ject.naspi.market.application;

/**
 * 종목 상장 이벤트.
 *
 * <p>상장이 저장된 같은 트랜잭션 안에서 발행된다. league는 이 이벤트를 받아 상장자를 대기자에서
 * 참여자로 바꾼다 (LEA-05).
 *
 * @param stockId 상장된 종목 ID
 * @param leagueId 리그 ID
 * @param listerPlayerId 상장자 플레이어 ID
 */
public record StockListed(long stockId, long leagueId, long listerPlayerId) {
}
