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
 * 리그 결과의 종목 평가 (ERD: league_result_stock). 리그의 종목마다 한 행이며, 종료 전 탈퇴자가 상장한 종목도
 * 포함한다 (LEA-13). 생성 후 바뀌지 않는다.
 */
@Entity
@Table(name = "league_result_stock", uniqueConstraints = @UniqueConstraint(
        name = "uk_league_result_stock", columnNames = {"league_result_id", "stock_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeagueResultStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_result_id", nullable = false, updatable = false)
    private Long leagueResultId;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "stock_id", nullable = false, updatable = false)
    private Long stockId;

    /** 종료 평가 가격 (1주당 원, MKT-07). */
    @Column(name = "final_evaluation_price", nullable = false, updatable = false)
    private long finalEvaluationPrice;

    private LeagueResultStock(long leagueResultId, long leagueId, long stockId,
            long finalEvaluationPrice) {
        if (finalEvaluationPrice < 0) {
            throw new IllegalArgumentException(
                    "종료 평가 가격은 음수일 수 없습니다. price=" + finalEvaluationPrice);
        }
        this.leagueResultId = leagueResultId;
        this.leagueId = leagueId;
        this.stockId = stockId;
        this.finalEvaluationPrice = finalEvaluationPrice;
    }

    /**
     * 종목의 종료 평가 가격을 기록한다.
     *
     * @param leagueResultId 리그 결과 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param finalEvaluationPrice 종료 평가 가격 (1주당 원)
     * @return 종목 평가
     */
    public static LeagueResultStock of(long leagueResultId, long leagueId, long stockId,
            long finalEvaluationPrice) {
        return new LeagueResultStock(leagueResultId, leagueId, stockId, finalEvaluationPrice);
    }
}
