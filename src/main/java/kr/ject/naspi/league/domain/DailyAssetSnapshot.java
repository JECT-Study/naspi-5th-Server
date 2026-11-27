package kr.ject.naspi.league.domain;

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
 * 거래일별 자산 집계 (ERD: daily_asset_snapshot, LEA-18). 거래일이 끝날 때마다 참여자별로 한 행이다. 생성 후 바뀌지
 * 않는다.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 결과"): 각 거래일 종료 시점의 전체 현금과 종목별 보유 수량을
 * 기준으로 집계하고, 보유 주식은 해당 거래일의 종가로 평가한다. 마지막 거래일은 종료 평가 가격으로 평가한 최종
 * 총자산이다. 총자산은 전체 현금과 보유 주식 평가금액의 합이다.
 *
 * <p>탈퇴한 참여자도 기록하며, 조회하는 쪽이 거른다. 수익률은 저장하지 않고 조회할 때 총자산으로 계산한다.
 */
@Entity
@Table(name = "daily_asset_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_asset_snapshot_league_day_player",
                columnNames = {"league_id", "trading_day", "player_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyAssetSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    /** 거래일 번호 (1~7). */
    @Column(name = "trading_day", nullable = false, updatable = false)
    private int tradingDay;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    /** 현금 (예수금, 원). 예약금을 포함한 전체 현금. */
    @Column(name = "cash", nullable = false, updatable = false)
    private long cash;

    /** 보유 주식 평가금액 합계 (원, 자사주 포함). */
    @Column(name = "stock_valuation", nullable = false, updatable = false)
    private long stockValuation;

    /** 총자산 (원, {@code 현금 + 보유 주식 평가금액}). */
    @Column(name = "total_asset", nullable = false, updatable = false)
    private long totalAsset;

    private DailyAssetSnapshot(long leagueId, int tradingDay, long playerId, long cash,
            long stockValuation, long totalAsset) {
        this.leagueId = leagueId;
        this.tradingDay = tradingDay;
        this.playerId = playerId;
        this.cash = cash;
        this.stockValuation = stockValuation;
        this.totalAsset = totalAsset;
    }

    /**
     * 참여자 한 명의 거래일 자산 집계를 만든다.
     *
     * @param leagueId 리그 ID
     * @param tradingDay 거래일 번호 (1~7)
     * @param playerId 플레이어 ID
     * @param cash 현금 (예수금, 원)
     * @param stockValuation 보유 주식 평가금액 합계 (원)
     * @param totalAsset 총자산 (원)
     * @return 거래일 자산 집계
     * @throws IllegalArgumentException 거래일 번호가 1~7이 아니거나, 금액이 음수이거나, 총자산이 현금과 보유 주식
     *     평가금액의 합이 아닌 경우
     */
    public static DailyAssetSnapshot of(long leagueId, int tradingDay, long playerId, long cash,
            long stockValuation, long totalAsset) {
        if (tradingDay < 1 || tradingDay > TradingSchedule.TRADING_DAYS) {
            throw new IllegalArgumentException("거래일 번호는 1~" + TradingSchedule.TRADING_DAYS
                    + "입니다: " + tradingDay);
        }
        if (cash < 0 || stockValuation < 0 || totalAsset < 0) {
            throw new IllegalArgumentException("거래일 자산 집계 금액은 음수일 수 없습니다: cash=" + cash
                    + ", stockValuation=" + stockValuation + ", totalAsset=" + totalAsset);
        }
        if (cash + stockValuation != totalAsset) {
            throw new IllegalArgumentException("총자산은 현금과 보유 주식 평가금액의 합이어야 합니다: cash="
                    + cash + ", stockValuation=" + stockValuation + ", totalAsset=" + totalAsset);
        }
        return new DailyAssetSnapshot(leagueId, tradingDay, playerId, cash, stockValuation,
                totalAsset);
    }
}
