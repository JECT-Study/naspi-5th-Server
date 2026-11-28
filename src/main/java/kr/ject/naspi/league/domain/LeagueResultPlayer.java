package kr.ject.naspi.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리그 결과의 참여자 결과 (ERD: league_result_player). 결과 대상 참여자(종료 전 탈퇴자 제외)마다 한 행이다
 * (LEA-13). 생성 후 바뀌지 않는다.
 *
 * <p>표시 이름은 리그 종료 시점의 회원 이름이다. 종료 후 탈퇴해도 이 행은 고치지 않고, 결과 조회에서
 * '탈퇴한 사용자'로 바꿔 보여준다 (LEA-14·LEA-15).
 */
@Entity
@Table(name = "league_result_player",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_league_result_player_player", columnNames = "player_id"),
        indexes = @Index(name = "idx_league_result_player_league", columnList = "league_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeagueResultPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_result_id", nullable = false, updatable = false)
    private Long leagueResultId;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "player_id", nullable = false, updatable = false)
    private Long playerId;

    /** 최종 순위 (1부터). 공동 순위면 같은 값이다. */
    @Column(name = "final_rank", nullable = false, updatable = false)
    private int finalRank;

    /** 최종 총자산 (원, 자사주 포함). */
    @Column(name = "final_total_asset", nullable = false, updatable = false)
    private long finalTotalAsset;

    /** 최종 현금 (원). */
    @Column(name = "final_cash", nullable = false, updatable = false)
    private long finalCash;

    /** 최종 손익 (원, {@code 최종 총자산 − 1억 원}). */
    @Column(name = "final_profit", nullable = false, updatable = false)
    private long finalProfit;

    /** 최종 수익률(%). 소수점 첫째 자리까지. */
    @Column(name = "final_return_rate", nullable = false, updatable = false,
            precision = 7, scale = 1)
    private BigDecimal finalReturnRate;

    /** 리그 종료 시점의 표시 이름. */
    @Column(name = "display_name", nullable = false, updatable = false)
    private String displayName;

    private LeagueResultPlayer(long leagueResultId, long leagueId, int finalRank,
            FinalAsset asset, String displayName) {
        this.leagueResultId = leagueResultId;
        this.leagueId = leagueId;
        this.playerId = asset.playerId();
        this.finalRank = finalRank;
        this.finalTotalAsset = asset.totalAsset();
        this.finalCash = asset.cash();
        this.finalProfit = asset.profit();
        this.finalReturnRate = asset.returnRate();
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * 순위가 매겨진 참여자의 최종 자산으로 참여자 결과를 만든다.
     *
     * @param leagueResultId 리그 결과 ID
     * @param leagueId 리그 ID
     * @param ranked 순위와 최종 자산
     * @param displayName 리그 종료 시점의 표시 이름
     * @return 참여자 결과
     */
    public static LeagueResultPlayer of(long leagueResultId, long leagueId,
            Ranked<FinalAsset> ranked, String displayName) {
        return new LeagueResultPlayer(leagueResultId, leagueId, ranked.rank(), ranked.entry(),
                displayName);
    }
}
