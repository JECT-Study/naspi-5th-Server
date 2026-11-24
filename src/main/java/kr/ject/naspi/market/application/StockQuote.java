package kr.ject.naspi.market.application;

import java.math.BigDecimal;
import kr.ject.naspi.market.domain.PriceChange;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;

/**
 * 종목 시세.
 *
 * @param currentPrice 현재가 (원)
 * @param basePrice 기준가 (원)
 * @param upperLimitPrice 상한가 (원)
 * @param lowerLimitPrice 하한가 (원)
 * @param changeAmount 기준가 대비 등락액 (원)
 * @param changeRate 기준가 대비 등락률 (%, 소수점 첫째 자리)
 * @param volume 현재 거래일 거래량 (주)
 */
public record StockQuote(long currentPrice, long basePrice, long upperLimitPrice,
        long lowerLimitPrice, long changeAmount, BigDecimal changeRate, long volume) {

    /**
     * 종목 현재가와 거래일 시세로 시세를 만든다.
     *
     * @param stock 종목 (현재가가 있어야 한다)
     * @param price 적용 중인 거래일 시세
     * @return 시세
     */
    public static StockQuote of(Stock stock, StockDailyPrice price) {
        long currentPrice = stock.getCurrentPrice();
        PriceChange change = PriceChange.of(currentPrice, price.getBasePrice());
        return new StockQuote(currentPrice, price.getBasePrice(), price.getUpperLimitPrice(),
                price.getLowerLimitPrice(), change.amount(), change.rate(), price.getVolume());
    }
}
