package kr.ject.naspi.market.application;

/**
 * 다른 모듈에 노출하는 리그 종목 정보.
 *
 * @param stockId 종목 ID
 * @param name 종목 이름
 * @param description 종목 소개
 * @param listerPlayerId 상장자 플레이어 ID
 * @param currentPrice 현재가 (1주당 원). 리그 시작 전에는 {@code null}
 */
public record LeagueStock(long stockId, String name, String description, long listerPlayerId,
        Long currentPrice) {
}
