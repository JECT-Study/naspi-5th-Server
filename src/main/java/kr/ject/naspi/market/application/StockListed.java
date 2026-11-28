package kr.ject.naspi.market.application;

/**
 * 종목 상장 이벤트. websocket이 커밋 이후에 받아 그 리그 상세를 발행한다 (대기실 실시간 반영, WS-07). 상장자를
 * 대기자에서 참여자로 바꾸는 처리(LEA-05)는 이 이벤트가 아니라 {@link StockListingService}가 league를 직접
 * 호출한다.
 *
 * <p>상장이 저장된 같은 트랜잭션 안에서 발행된다. 리그 생성 중 방장 종목 상장에서도 발행된다. 구독하는 쪽은
 * 커밋 후에 받아야 한다.
 *
 * @param stockId 상장된 종목 ID
 * @param leagueId 리그 ID
 * @param listerPlayerId 상장자 플레이어 ID
 */
public record StockListed(long stockId, long leagueId, long listerPlayerId) {
}
