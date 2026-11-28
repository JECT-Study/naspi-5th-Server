package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.contents.domain.Report;
import kr.ject.naspi.contents.domain.ReportBody;
import kr.ject.naspi.contents.domain.ReportRepository;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueTradingDay;
import kr.ject.naspi.market.application.StockQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 제보 작성.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 제보 기반 AI 뉴스"): 참여자는 다른 참여자의 종목에 제보를
 * 작성할 수 있고 자기 종목에는 제보할 수 없다. 상장자가 탈퇴한 종목에도 제보할 수 있다. 작성 횟수는 제한하지
 * 않고, 본문 1~300자, 이미지는 선택이며 JPEG·PNG로 최대 4개다. 제보와 함께 생성 중(PENDING)인 제보 기반 AI
 * 뉴스를 만든다. 제보는 진행 중인 리그에서만 작성할 수 있다 (docs/issues/v1/contents.md CNT-13 결정).
 *
 * <p>이미지 파일은 GCS 같은 외부 저장소에 올리므로, 그동안 DB 트랜잭션을 열어 두지 않는다. 검증 → 파일 업로드
 * (트랜잭션 밖) → 제보·이미지 메타데이터·생성 중 뉴스 저장과 이벤트 발행(한 트랜잭션) 순서로 처리하고, 저장이
 * 실패하면 올린 파일을 지운다. 그래서 이 서비스는 호출하는 쪽의 트랜잭션 안에서 부르면 안 된다.
 */
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final NewsRepository newsRepository;
    private final ImageService imageService;
    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 제보 서비스를 만든다.
     *
     * @param reportRepository 제보 저장소
     * @param newsRepository 뉴스 저장소
     * @param imageService 이미지 서비스
     * @param leagueQueryService 리그 상태·플레이어 조회
     * @param stockQueryService 종목 상장자 조회
     * @param eventPublisher 제보 작성 이벤트 발행
     * @param transactionManager 제보 저장 트랜잭션
     * @param clock 현재 시각
     */
    public ReportService(ReportRepository reportRepository, NewsRepository newsRepository,
                         ImageService imageService, LeagueQueryService leagueQueryService,
                         StockQueryService stockQueryService,
                         ApplicationEventPublisher eventPublisher,
                         PlatformTransactionManager transactionManager, Clock clock) {
        this.reportRepository = reportRepository;
        this.newsRepository = newsRepository;
        this.imageService = imageService;
        this.leagueQueryService = leagueQueryService;
        this.stockQueryService = stockQueryService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 다른 참여자의 종목에 제보를 작성하고 AI 뉴스 생성을 요청한다.
     *
     * @param memberId 제보하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 대상 종목 ID
     * @param body 본문
     * @param images 첨부 이미지 (첨부 순서대로, 없으면 빈 목록)
     * @return 작성된 제보
     * @throws NaspiBizException 본문·이미지가 규칙에 맞지 않거나, 리그가 없거나 진행 중이 아니거나,
     *         리그에 입장하지 않았거나, 종목이 없거나 본인이 상장한 종목인 경우
     */
    public SubmittedReport submit(long memberId, long leagueId, long stockId, String body,
                                  List<ImageFile> images) {
        ReportBody reportBody = ReportBody.of(body);
        // 리그가 없으면(204) 여기서 오류
        Optional<LeagueTradingDay> currentTradingDay =
                leagueQueryService.findCurrentTradingDay(leagueId);
        // 입장하지 않았거나(004) 종료 시각이 지난 리그면(212) 여기서 오류
        long playerId = leagueQueryService.requireAccessiblePlayer(memberId, leagueId);
        // 남은 경우는 시작 전(대기실) 리그
        LeagueTradingDay tradingDay = currentTradingDay
                .orElseThrow(() -> new NaspiBizException(ErrorCode.REPORT_LEAGUE_NOT_STARTED));
        // 진행 중인 리그는 시작 조건상 대기자가 없고 시작 후에는 입장할 수 없으므로, 입장한 플레이어는 모두
        // 상장을 마친 참여자다. 그래서 상장 완료 여부는 따로 확인하지 않는다.
        // 리그에 없는 종목이면(304) 여기서 오류
        if (stockQueryService.isListedBy(leagueId, stockId, playerId)) {
            throw new NaspiBizException(ErrorCode.REPORT_OWN_STOCK);
        }

        List<StoredImage> stored = imageService.store(leagueId, images);
        try {
            return transactionTemplate.execute(status -> {
                LocalDateTime now = LocalDateTime.now(clock);
                Report saved = reportRepository.save(Report.submit(leagueId, stockId, playerId,
                        reportBody, tradingDay.dayNumber(), now));
                List<Long> imageIds = imageService.attachToReport(leagueId, saved.getId(), stored);
                News news = newsRepository.save(News.requestFromReport(saved, now));
                eventPublisher.publishEvent(
                        new ReportSubmitted(saved.getId(), leagueId, stockId, playerId));
                return new SubmittedReport(saved.getId(), stockId, saved.getBody().getValue(),
                        saved.getTradingDay(), saved.getCreatedAt(), imageIds, news.getId());
            });
        } catch (RuntimeException e) {
            imageService.discard(stored);
            throw e;
        }
    }
}
