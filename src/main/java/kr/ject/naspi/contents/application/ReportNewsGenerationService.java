package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.contents.domain.Report;
import kr.ject.naspi.contents.domain.ReportNewsGenerationResult;
import kr.ject.naspi.contents.domain.ReportNewsGenerator;
import kr.ject.naspi.contents.domain.ReportNewsSource;
import kr.ject.naspi.contents.domain.ReportRepository;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 제보 기반 AI 뉴스 생성. 생성 중(PENDING) 뉴스를 AI 결과로 채워 발행·미생성·실패 중 하나로 만든다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 제보 기반 AI 뉴스"): 대상 종목 이름·제보자 이름·제보
 * 본문·첨부 이미지로 기사를 쓰고, 최초 요청을 포함해 최대 3회 시도하며({@link ReportNewsGenerator}), 미생성
 * (제보 내용 부족)은 재시도하지 않는다. 뉴스 생성 전에 제보자가 탈퇴했으면 생성기를 호출하지 않고 실패로
 * 처리한다. 생성 결과와 관계없이 제보 원문은 유지된다.
 *
 * <p>AI 호출 동안 DB 트랜잭션을 열어 두지 않으므로, 호출하는 쪽의 트랜잭션 안에서 부르면 안 된다.
 *
 * <p>TODO: PENDING에 멈춘 뉴스 복구는 공시 뉴스와 함께 처리한다 (CNT-07).
 */
@Slf4j
@Service
public class ReportNewsGenerationService {

    private final NewsRepository newsRepository;
    private final ReportRepository reportRepository;
    private final ImageService imageService;
    private final ReportNewsGenerator reportNewsGenerator;
    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 제보 뉴스 생성 서비스를 만든다.
     *
     * @param newsRepository 뉴스 저장소
     * @param reportRepository 제보 저장소
     * @param imageService 제보 이미지 URL 발급
     * @param reportNewsGenerator 제보 뉴스 생성기
     * @param leagueQueryService 제보자 이름·탈퇴 여부 조회
     * @param stockQueryService 종목 이름 조회
     * @param transactionManager 뉴스 저장 트랜잭션
     * @param clock 발행 일시 기준 시계
     */
    public ReportNewsGenerationService(NewsRepository newsRepository,
                                       ReportRepository reportRepository,
                                       ImageService imageService,
                                       ReportNewsGenerator reportNewsGenerator,
                                       LeagueQueryService leagueQueryService,
                                       StockQueryService stockQueryService,
                                       PlatformTransactionManager transactionManager,
                                       Clock clock) {
        this.newsRepository = newsRepository;
        this.reportRepository = reportRepository;
        this.imageService = imageService;
        this.reportNewsGenerator = reportNewsGenerator;
        this.leagueQueryService = leagueQueryService;
        this.stockQueryService = stockQueryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 제보의 생성 중(PENDING) 뉴스를 AI로 채우고 상태를 갱신한다. 생성 중인 뉴스가 없으면 아무것도 하지 않는다.
     *
     * @param reportId 제보 ID
     */
    public void generate(long reportId) {
        Report report = transactionTemplate.execute(status -> prepare(reportId));
        if (report == null) {
            return;
        }

        ReportNewsGenerationResult result = callGenerator(report);

        transactionTemplate.executeWithoutResult(status -> complete(reportId, result));
    }

    /**
     * 생성 중인 뉴스가 있으면 제보를 돌려준다. 없으면 null.
     */
    private Report prepare(long reportId) {
        News news = newsRepository.findByReportId(reportId).orElse(null);
        if (news == null) {
            log.warn("제보에 생성 중 뉴스가 없음: reportId={}", reportId);
            return null;
        }
        if (news.getStatus() != NewsStatus.PENDING) {
            log.info("이미 결과가 반영된 제보 뉴스: reportId={}, status={}", reportId,
                    news.getStatus());
            return null;
        }
        return reportRepository.findById(reportId).orElseThrow();
    }

    /**
     * AI로 뉴스를 생성한다. 제보자가 탈퇴했거나 입력을 준비하지 못하면 생성기를 부르지 않고 실패 결과를 돌려준다.
     */
    private ReportNewsGenerationResult callGenerator(Report report) {
        ReportNewsSource source;
        try {
            if (leagueQueryService.isPlayerWithdrawn(report.getLeagueId(),
                    report.getReporterPlayerId())) {
                log.warn("제보자 탈퇴로 제보 뉴스 생성 실패: reportId={}", report.getId());
                return new ReportNewsGenerationResult.Failed("제보자가 탈퇴함");
            }
            source = toSource(report);
        } catch (RuntimeException e) {
            log.warn("제보 뉴스 생성 준비 실패: reportId={}", report.getId(), e);
            return new ReportNewsGenerationResult.Failed(e.getMessage());
        }
        return reportNewsGenerator.generate(source);
    }

    private ReportNewsSource toSource(Report report) {
        // 리그 시작 후에는 종목 이름을 바꿀 수 없어 작성 시점의 이름과 같다
        String stockName = stockQueryService.findAllByLeagueId(report.getLeagueId()).stream()
                .filter(stock -> stock.stockId() == report.getStockId())
                .map(LeagueStock::name)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "제보 대상 종목을 찾을 수 없음: stockId=" + report.getStockId()));
        String reporterName = leagueQueryService.getPlayerNames(report.getLeagueId(),
                        List.of(report.getReporterPlayerId()))
                .get(report.getReporterPlayerId());
        // AI 제공자가 이미지를 직접 내려받는 주소
        List<String> imageUrls = imageService.issueReportUrls(report.getId()).stream()
                .map(ImageUrl::signedUrl)
                .toList();
        return new ReportNewsSource(stockName, reporterName, report.getBody().getValue(),
                imageUrls);
    }

    private void complete(long reportId, ReportNewsGenerationResult result) {
        News news = newsRepository.findByReportId(reportId).orElseThrow();
        news.complete(result, LocalDateTime.now(clock));
        switch (result) {
            case ReportNewsGenerationResult.Generated generated ->
                    log.info("제보 AI 뉴스 발행: reportId={}, newsId={}, sentiment={}", reportId,
                            news.getId(), generated.sentiment());
            case ReportNewsGenerationResult.Skipped skipped ->
                    log.info("제보 AI 뉴스 미생성: reportId={}, reason={}", reportId,
                            skipped.reason());
            case ReportNewsGenerationResult.Failed failed ->
                    log.warn("제보 AI 뉴스 생성 실패: reportId={}, reason={}", reportId,
                            failed.reason());
        }
    }
}
