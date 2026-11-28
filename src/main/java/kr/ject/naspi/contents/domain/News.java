package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 뉴스 (ERD: news).
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 공시 기반 AI 뉴스", "뉴스 - 제보 기반 AI 뉴스"): 종목과
 * 다대일이고, 공시 기반 뉴스는 공시와({@code disclosure_id} 유니크), 제보 기반 뉴스는 제보와
 * ({@code report_id} 유니크) 일대일이다. 제목 최대 30자, 본문 최대 100자, 이미지 없음, 삭제 불가.
 * 제목·본문·호재/악재는 발행된 뉴스에만 있다.
 */
@Entity
@Table(name = "news",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_news_disclosure_id", columnNames = "disclosure_id"),
                @UniqueConstraint(name = "uk_news_report_id", columnNames = "report_id")},
        indexes = @Index(name = "idx_news_stock_id", columnList = "stock_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class News {

    /** 같은 뉴스를 다시 생성 요청할 수 있는 최소 간격. */
    public static final Duration REGENERATION_INTERVAL = Duration.ofMinutes(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "stock_id", nullable = false, updatable = false)
    private Long stockId;

    /** 공시 기반 AI 뉴스의 원본 공시. 공시 없이 만드는 뉴스(v2 일일 요약 등)를 위해 비워 둘 수 있다. */
    @Column(name = "disclosure_id", updatable = false)
    private Long disclosureId;

    /** 제보 기반 AI 뉴스의 원본 제보. 공시 기반 뉴스는 비어 있다. */
    @Column(name = "report_id", updatable = false)
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "news_type", nullable = false, updatable = false, length = 20)
    private NewsType type;

    /** 호재·악재. 발행된 뉴스에만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment", length = 20)
    private NewsSentiment sentiment;

    /** 제목. 발행된 뉴스에만 있다. */
    @Column(name = "title", length = NewsGenerationResult.Generated.TITLE_MAX_LENGTH)
    private String title;

    /** 본문. 발행된 뉴스에만 있다. */
    @Column(name = "body", length = NewsGenerationResult.Generated.BODY_MAX_LENGTH)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NewsStatus status;

    /** 원본 공시·제보를 작성한 거래일 번호 (1~7). */
    @Column(name = "trading_day", nullable = false, updatable = false)
    private int tradingDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 발행된 시각. 발행된 뉴스에만 있다. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 마지막으로 수동 재생성을 요청한 시각. 요청한 적이 없으면 null. */
    @Column(name = "regeneration_requested_at")
    private LocalDateTime regenerationRequestedAt;

    private News(long leagueId, long stockId, Long disclosureId, Long reportId, NewsType type,
                 int tradingDay, LocalDateTime createdAt) {
        this.leagueId = leagueId;
        this.stockId = stockId;
        this.disclosureId = disclosureId;
        this.reportId = reportId;
        this.type = type;
        this.status = NewsStatus.PENDING;
        this.tradingDay = tradingDay;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 공시의 생성 중(PENDING) 뉴스를 만든다.
     *
     * @param disclosure 원본 공시 (저장되어 ID가 있어야 한다)
     * @param createdAt 작성 일시
     * @return 생성 중인 뉴스 (저장 전)
     */
    public static News requestFrom(Disclosure disclosure, LocalDateTime createdAt) {
        return new News(disclosure.getLeagueId(), disclosure.getStockId(), disclosure.getId(),
                null, NewsType.DISCLOSURE, disclosure.getTradingDay(), createdAt);
    }

    /**
     * 제보의 생성 중(PENDING) 뉴스를 만든다. 제보 뉴스는 원본 공시가 없다.
     *
     * @param report 원본 제보 (저장되어 ID가 있어야 한다)
     * @param createdAt 작성 일시
     * @return 생성 중인 뉴스 (저장 전)
     */
    public static News requestFromReport(Report report, LocalDateTime createdAt) {
        return new News(report.getLeagueId(), report.getStockId(), null, report.getId(),
                NewsType.REPORT, report.getTradingDay(), createdAt);
    }

    /**
     * AI 생성 결과를 반영한다. 생성되면 제목·본문·호재/악재를 채워 발행하고, 미생성·실패는 상태만 바꾼다.
     * 생성 중인 뉴스에만 한 번 반영할 수 있다.
     *
     * @param result 생성 결과
     * @param now 반영 일시 (발행되면 발행 일시가 된다)
     * @throws IllegalStateException 생성 중인 뉴스가 아닌 경우
     */
    public void complete(NewsGenerationResult result, LocalDateTime now) {
        requirePending();
        switch (result) {
            case NewsGenerationResult.Generated generated -> {
                this.title = generated.title();
                this.body = generated.body();
                this.sentiment = generated.sentiment();
                this.status = NewsStatus.PUBLISHED;
                this.publishedAt = Objects.requireNonNull(now, "now");
            }
            case NewsGenerationResult.Skipped skipped -> this.status = NewsStatus.SKIPPED;
            case NewsGenerationResult.Failed failed -> this.status = NewsStatus.FAILED;
        }
    }

    /**
     * 제보 뉴스의 AI 생성 결과를 반영한다. 생성되면 제목·본문·호재/악재를 채워 발행하고, 미생성·실패는 상태만
     * 바꾼다. 생성 중인 뉴스에만 한 번 반영할 수 있다.
     *
     * @param result 제보 뉴스 생성 결과
     * @param now 반영 일시 (발행되면 발행 일시가 된다)
     * @throws IllegalStateException 생성 중인 뉴스가 아닌 경우
     */
    public void complete(ReportNewsGenerationResult result, LocalDateTime now) {
        requirePending();
        switch (result) {
            case ReportNewsGenerationResult.Generated generated -> {
                this.title = generated.title();
                this.body = generated.body();
                this.sentiment = generated.sentiment();
                this.status = NewsStatus.PUBLISHED;
                this.publishedAt = Objects.requireNonNull(now, "now");
            }
            case ReportNewsGenerationResult.Skipped skipped -> this.status = NewsStatus.SKIPPED;
            case ReportNewsGenerationResult.Failed failed -> this.status = NewsStatus.FAILED;
        }
    }

    private void requirePending() {
        if (status != NewsStatus.PENDING) {
            throw new IllegalStateException("생성 중인 뉴스가 아닙니다: id=%d, status=%s"
                    .formatted(id, status));
        }
    }

    /**
     * 실패한 뉴스의 수동 재생성을 요청한다. 생성 중 상태로 되돌린다.
     *
     * <p>정책: 자동 재시도까지 실패(FAILED)한 뉴스만 다시 생성할 수 있다. 결정(CNT-07): 뉴스당 1분에 한 번만
     * 요청할 수 있고, 직전 요청으로부터 정확히 1분이 지난 시각부터 허용한다.
     *
     * @param now 요청 일시
     * @throws NaspiBizException 실패한 뉴스가 아니거나, 직전 요청 후 1분이 지나지 않은 경우
     */
    public void requestRegeneration(LocalDateTime now) {
        if (status != NewsStatus.FAILED) {
            throw new NaspiBizException(ErrorCode.NEWS_NOT_REGENERATABLE);
        }
        if (regenerationRequestedAt != null
                && now.isBefore(regenerationRequestedAt.plus(REGENERATION_INTERVAL))) {
            throw new NaspiBizException(ErrorCode.NEWS_REGENERATION_TOO_SOON);
        }
        this.status = NewsStatus.PENDING;
        this.regenerationRequestedAt = Objects.requireNonNull(now, "now");
    }
}
