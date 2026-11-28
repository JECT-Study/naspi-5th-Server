package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제보 (ERD: report). 참여자가 다른 참여자의 종목(대상 종목)에서 일어난 일상적인 사건을 알리는 글이다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 제보 기반 AI 뉴스"): 작성 횟수는 무제한이고, 작성한 뒤에는
 * 수정할 수 없다. 제목은 없다. 이미지는 {@link ReportImage}로 따로 저장한다.
 */
@Entity
@Table(name = "report",
        indexes = @Index(name = "idx_report_stock_id", columnList = "stock_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    /** 대상 종목. */
    @Column(name = "stock_id", nullable = false, updatable = false)
    private Long stockId;

    @Column(name = "reporter_player_id", nullable = false, updatable = false)
    private Long reporterPlayerId;

    @Embedded
    private ReportBody body;

    /** 작성한 거래일 번호 (1~7). */
    @Column(name = "trading_day", nullable = false, updatable = false)
    private int tradingDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Report(long leagueId, long stockId, long reporterPlayerId, ReportBody body,
                   int tradingDay, LocalDateTime createdAt) {
        this.leagueId = leagueId;
        this.stockId = stockId;
        this.reporterPlayerId = reporterPlayerId;
        this.body = Objects.requireNonNull(body, "body");
        this.tradingDay = tradingDay;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 제보를 작성한다. 제보자가 리그 참여자인지, 자기 종목이 아닌지, 리그가 진행 중인지는 호출하는 쪽이 먼저
     * 확인한다.
     *
     * @param leagueId 리그 ID
     * @param stockId 대상 종목 ID
     * @param reporterPlayerId 제보자 플레이어 ID
     * @param body 본문
     * @param tradingDay 작성한 거래일 번호
     * @param createdAt 작성 일시
     * @return 작성된 제보 (저장 전)
     */
    public static Report submit(long leagueId, long stockId, long reporterPlayerId,
                                ReportBody body, int tradingDay, LocalDateTime createdAt) {
        return new Report(leagueId, stockId, reporterPlayerId, body, tradingDay, createdAt);
    }
}
