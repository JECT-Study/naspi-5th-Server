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
 * <p>현재는 거래일의 거래량만 담는다. 거래량은 실제 체결 수량의 합계이며(docs/policies/v1/domain/market.md
 * "시장"), 체결 1건당 1회 합산한다. 합산은 리그 락 안의 체결 트랜잭션에서만 일어나므로 동시 갱신이 없다.
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
    private static final int FIRST_TRADING_DAY = 1;

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

    private StockDailyPrice(long leagueId, long stockId, int tradingDay) {
        this.leagueId = leagueId;
        this.stockId = stockId;
        this.tradingDay = tradingDay;
        this.volume = 0;
    }

    /**
     * 거래량이 0인 거래일 시세를 만든다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param tradingDay 거래일 번호 (1~7)
     * @return 거래량 0인 거래일 시세
     * @throws IllegalArgumentException 거래일 번호가 1~7이 아닌 경우
     */
    public static StockDailyPrice start(long leagueId, long stockId, int tradingDay) {
        if (tradingDay < FIRST_TRADING_DAY || tradingDay > LAST_TRADING_DAY) {
            throw new IllegalArgumentException(
                    "거래일 번호는 1~7이어야 합니다: " + tradingDay);
        }
        return new StockDailyPrice(leagueId, stockId, tradingDay);
    }

    /**
     * 체결 수량을 거래량에 더한다. 체결 1건당 한 번 호출한다.
     *
     * @param quantity 체결 수량 (주)
     * @throws IllegalArgumentException 수량이 0 이하인 경우
     * @throws ArithmeticException 합계가 {@code long} 범위를 넘는 경우
     */
    public void addVolume(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("체결 수량은 0보다 커야 합니다: " + quantity);
        }
        this.volume = Math.addExact(volume, quantity);
    }
}
