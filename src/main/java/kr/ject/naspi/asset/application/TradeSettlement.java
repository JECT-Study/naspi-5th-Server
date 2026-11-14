package kr.ject.naspi.asset.application;

/**
 * 정산할 체결 1건. order가 매칭 직후 {@link AssetSettlementService#settle}에 넘긴다.
 *
 * @param leagueId 리그 ID
 * @param stockId 종목 ID
 * @param buyerPlayerId 매수자 플레이어 ID
 * @param sellerPlayerId 매도자 플레이어 ID. 매수자와 달라야 한다 (자전거래 금지)
 * @param buyReservedPrice 매수 주문의 예약 기준 가격 (지정가: 지정 가격, 시장가: 보호 조건 내 최고 체결 가능 가격)
 * @param price 체결가. 매수 예약 기준 가격 이하
 * @param quantity 체결 수량
 */
public record TradeSettlement(long leagueId, long stockId, long buyerPlayerId,
        long sellerPlayerId, long buyReservedPrice, long price, long quantity) {
}
