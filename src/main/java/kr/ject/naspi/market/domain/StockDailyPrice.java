package kr.ject.naspi.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 종목 일별 시세 (ERD: stock_daily_price). 거래일마다 종목당 한 행이다.
 *
 * <p>행은 리그 시작(1일차, 기준가 = 시초가)과 거래일 전환(다음 거래일, 기준가 = 직전 거래일 종가)에서 만든다
 * (docs/policies/v1/domain/market.md "시장", "가격제한폭"). 기준가·상한가·하한가는 행을 만들 때 정해지고,
 * 종가는 그 거래일을 마감할 때 확정된다(마감 전에는 {@code null}). 종가는 그 거래일의 마지막 체결가이며
 * 거래량이 0이면 기준가다.
 *
 * <p>거래량은 실제 체결 수량의 합계이며, 체결 1건당 1회 합산한다. 합산은 리그 락 안의 체결 트랜잭션에서만
 * 일어나므로 동시 갱신이 없다.
 * 리그·종목은 FK 없이 ID로만 참조하고, 거래일은 ERD의 거래일 FK 대신 {@code Trade}·{@code Order}와 같이
 * 거래일 번호로 둔다.
 */
@Entity
@Table(name = "stock_daily_price",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_daily_price_stock_day",
                columnNames = {"stock_id", "trading_day"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDailyPrice {

    /** 거래일 번호의 최솟값. 리그는 7일간 진행된다. */
    public static final int FIRST_TRADING_DAY = 1;

    /** 거래일 번호의 최댓값. */
    private static final int LAST_TRADING_DAY = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    /** 거래일 번호 (1~7). */
    @Column(name = "trading_day", nullable = false)
    private int tradingDay;

    /** 거래량 (주). 그 거래일 체결 수량의 합계. */
    @Column(name = "volume", nullable = false)
    private long volume;

    /** 기준가 (원). 직전 거래일 종가, 1일차는 시초가. */
    @Column(name = "base_price", nullable = false)
    private long basePrice;

    /** 상한가 (원). */
    @Column(name = "upper_limit_price", nullable = false)
    private long upperLimitPrice;

    /** 하한가 (원). */
    @Column(name = "lower_limit_price", nullable = false)
    private long lowerLimitPrice;

    /** 종가 (원). 거래일을 마감하기 전에는 {@code null}. */
    @Column(name = "close_price")
    private Long closePrice;

    private StockDailyPrice(long leagueId, long stockId, int tradingDay, long basePrice,
            PriceLimit limit) {
        this.leagueId = leagueId;
        this.stockId = stockId;
        this.tradingDay = tradingDay;
        this.volume = 0;
        this.basePrice = basePrice;
        this.upperLimitPrice = limit.upper();
        this.lowerLimitPrice = limit.lower();
    }

    /**
     * 거래량 0, 종가 미확정인 거래일 시세를 만든다. 기준가로 상·하한가를 계산해 저장한다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param tradingDay 거래일 번호 (1~7)
     * @param basePrice 기준가 (100원 이상, 100원 단위)
     * @return 새 거래일 시세
     * @throws IllegalArgumentException 거래일 번호가 1~7이 아니거나 기준가가 올바르지 않은 경우
     */
    public static StockDailyPrice open(long leagueId, long stockId, int tradingDay,
            long basePrice) {
        if (tradingDay < FIRST_TRADING_DAY || tradingDay > LAST_TRADING_DAY) {
            throw new IllegalArgumentException(
                    "거래일 번호는 1~7이어야 합니다: " + tradingDay);
        }
        return new StockDailyPrice(leagueId, stockId, tradingDay, basePrice,
                PriceLimit.of(basePrice));
    }

    /** 저장된 상·하한가. */
    public PriceLimit priceLimit() {
        return new PriceLimit(lowerLimitPrice, upperLimitPrice);
    }

    /**
     * 거래일을 마감하고 종가를 확정한다. 종가는 마지막 체결가이며, 거래량이 0이면 기준가다.
     *
     * @param lastTradePrice 마지막 체결가 (종목 현재가)
     * @return 확정된 종가
     * @throws IllegalStateException 이미 마감한 경우
     */
    public long close(long lastTradePrice) {
        if (isClosed()) {
            throw new IllegalStateException("이미 마감한 거래일입니다: stockId=" + stockId
                    + ", tradingDay=" + tradingDay);
        }
        this.closePrice = volume == 0 ? basePrice : lastTradePrice;
        return closePrice;
    }

    public boolean isClosed() {
        return closePrice != null;
    }

    /**
     * 체결 수량을 거래량에 더한다. 체결 1건당 한 번 호출한다.
     *
     * @param quantity 체결 수량 (주)
     * @throws IllegalArgumentException 수량이 0 이하인 경우
     * @throws IllegalStateException 마감한 거래일인 경우
     * @throws ArithmeticException 합계가 {@code long} 범위를 넘는 경우
     */
    public void addVolume(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("체결 수량은 0보다 커야 합니다: " + quantity);
        }
        if (isClosed()) {
            throw new IllegalStateException("마감한 거래일에는 체결 수량을 더할 수 없습니다: stockId="
                    + stockId + ", tradingDay=" + tradingDay);
        }
        this.volume = Math.addExact(volume, quantity);
    }
}
