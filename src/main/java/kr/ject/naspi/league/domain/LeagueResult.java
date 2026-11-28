package kr.ject.naspi.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리그 결과 (ERD: league_result). 리그 종료 시 확정되는 결과 스냅샷의 머리 행이며 리그와 1:1이다 (LEA-13).
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 결과"): 최종 결과 페이지는 영구 보관한다. 생성 후 바뀌지
 * 않으며 삭제하지 않는다.
 */
@Entity
@Table(name = "league_result", uniqueConstraints = @UniqueConstraint(
        name = "uk_league_result_league", columnNames = "league_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeagueResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    /** 결과 확정 일시. 리그 종료 처리를 실행한 시각이다. */
    @Column(name = "finalized_at", nullable = false, updatable = false)
    private LocalDateTime finalizedAt;

    private LeagueResult(long leagueId, LocalDateTime finalizedAt) {
        this.leagueId = leagueId;
        this.finalizedAt = Objects.requireNonNull(finalizedAt, "finalizedAt");
    }

    /**
     * 리그 결과를 만든다.
     *
     * @param leagueId 리그 ID
     * @param finalizedAt 결과 확정 일시
     * @return 리그 결과
     */
    public static LeagueResult of(long leagueId, LocalDateTime finalizedAt) {
        return new LeagueResult(leagueId, finalizedAt);
    }
}
