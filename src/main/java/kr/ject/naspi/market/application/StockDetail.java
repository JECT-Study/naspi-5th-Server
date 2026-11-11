package kr.ject.naspi.market.application;

/**
 * 종목 이름과 소개.
 *
 * @param stockId 종목 ID
 * @param name 종목 이름
 * @param description 종목 소개
 */
public record StockDetail(long stockId, String name, String description) {
}
