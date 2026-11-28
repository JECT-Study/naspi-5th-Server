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
 * 참여자 결과의 종목별 보유 (ERD: league_result_holding). 보유 수량이 있는 종목만 한 행씩 저장한다 (LEA-13).
 * 자사주 여부는 저장하지 않는다. 생성 후 바뀌지 않는다.
 */
@Entity
@Table(name = "league_result_holding", uniqueConstraints = @UniqueConstraint(
        name = "uk_league_result_holding",
        columnNames = {"league_result_player_id", "stock_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeagueResultHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_result_player_id", nullable = false, updatable = false)
    private Long leagueResultPlayerId;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "stock_id", nullable = false, updatable = false)
    private Long stockId;

    /** 보유 수량 (주). */
    @Column(name = "quantity", nullable = false, updatable = false)
    private long quantity;

    /** 평가액 (원, {@code 보유 수량 × 종료 평가 가격}). */
    @Column(name = "valuation", nullable = false, updatable = false)
    private long valuation;

    private LeagueResultHolding(long leagueResultPlayerId, long leagueId, FinalHolding holding) {
        this.leagueResultPlayerId = leagueResultPlayerId;
        this.leagueId = leagueId;
        this.stockId = holding.stockId();
        this.quantity = holding.quantity();
        this.valuation = holding.valuation();
    }

    /**
     * 참여자 결과의 종목별 보유를 만든다.
     *
     * @param leagueResultPlayerId 참여자 결과 ID
     * @param leagueId 리그 ID
     * @param holding 종목 보유 평가
     * @return 종목별 보유
     */
    public static LeagueResultHolding of(long leagueResultPlayerId, long leagueId,
            FinalHolding holding) {
        return new LeagueResultHolding(leagueResultPlayerId, leagueId, holding);
    }
}
