package kr.ject.naspi.market.domain;

/**
 * 캐시에 담는 종목 정보. 엔티티를 캐시에 두지 않도록 화면 표시에 필요한 값만 복사한다.
 *
 * @param stockId 종목 ID
 * @param leagueId 리그 ID
 * @param name 종목 이름
 * @param description 종목 소개
 * @param listerPlayerId 상장자 플레이어 ID
 * @param currentPrice 현재가 (1주당 원). 정해지지 않았으면 {@code null}
 */
public record StockSnapshot(long stockId, long leagueId, String name, String description,
        long listerPlayerId, Long currentPrice) {

    /**
     * 종목의 현재 값을 복사한다.
     *
     * @param stock 종목
     * @return 종목 정보
     */
    public static StockSnapshot from(Stock stock) {
        return new StockSnapshot(stock.getId(), stock.getLeagueId(), stock.getName().getValue(),
                stock.getDescription().getValue(), stock.getListerPlayerId(),
                stock.getCurrentPrice());
    }
}
