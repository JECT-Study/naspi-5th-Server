package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.contents.domain.DisclosureTitle;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
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
 * 공시 작성·삭제.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): 상장자는 자기 종목에 공시를 작성할 수 있고 작성 횟수는
 * 제한하지 않는다. 제목 1~30자, 본문 1~300자, 이미지는 선택이며 JPEG·PNG로 최대 4개다. 공시와 함께 생성 중(PENDING)인
 * 공시 기반 AI 뉴스를 만든다. 공시는 진행 중인 리그에서만 작성할 수 있다 (docs/issues/v1/contents.md CNT-01 결정).
 *
 * <p>이미지 파일은 GCS 같은 외부 저장소에 올리므로, 그동안 DB 트랜잭션을 열어 두지 않는다. 검증 → 파일 업로드
 * (트랜잭션 밖) → 공시·이미지 메타데이터·생성 중 뉴스 저장과 이벤트 발행(한 트랜잭션) 순서로 처리하고, 저장이
 * 실패하면 올린 파일을 지운다. 그래서 이 서비스는 호출하는 쪽의 트랜잭션 안에서 부르면 안 된다.
 */
@Service
public class DisclosureService {

    private final DisclosureRepository disclosureRepository;
    private final NewsRepository newsRepository;
    private final ImageService imageService;
    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 공시 서비스를 만든다.
     *
     * @param disclosureRepository 공시 저장소
     * @param newsRepository 뉴스 저장소
     * @param imageService 이미지 서비스
     * @param leagueQueryService 리그 상태·플레이어 조회
     * @param stockQueryService 종목 상장자 조회
     * @param eventPublisher 공시 게시 이벤트 발행
     * @param transactionManager 공시 저장 트랜잭션
     * @param clock 현재 시각
     */
    public DisclosureService(DisclosureRepository disclosureRepository,
                             NewsRepository newsRepository,
                             ImageService imageService, LeagueQueryService leagueQueryService,
                             StockQueryService stockQueryService,
                             ApplicationEventPublisher eventPublisher,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.disclosureRepository = disclosureRepository;
        this.newsRepository = newsRepository;
        this.imageService = imageService;
        this.leagueQueryService = leagueQueryService;
        this.stockQueryService = stockQueryService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 자신이 상장한 종목에 공시를 작성하고 AI 뉴스 생성을 요청한다.
     *
     * @param memberId 작성하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param title 제목
     * @param body 본문
     * @param images 첨부 이미지 (첨부 순서대로, 없으면 빈 목록)
     * @return 작성된 공시
     * @throws NaspiBizException 제목·본문·이미지가 규칙에 맞지 않거나, 리그가 없거나 진행 중이 아니거나,
     *         리그에 입장하지 않았거나, 종목이 없거나 본인이 상장한 종목이 아닌 경우
     */
    public PublishedDisclosure publish(long memberId, long leagueId, long stockId, String title,
                                       String body, List<ImageFile> images) {
        DisclosureTitle disclosureTitle = DisclosureTitle.of(title);
        DisclosureBody disclosureBody = DisclosureBody.of(body);
        LeagueTradingDay tradingDay = leagueQueryService.findCurrentTradingDay(leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.DISCLOSURE_LEAGUE_NOT_STARTED));
        long playerId = leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
        if (!stockQueryService.isListedBy(leagueId, stockId, playerId)) {
            throw new NaspiBizException(ErrorCode.DISCLOSURE_STOCK_NOT_OWNED);
        }

        List<StoredImage> stored = imageService.store(leagueId, images);
        try {
            return transactionTemplate.execute(status -> {
                LocalDateTime now = LocalDateTime.now(clock);
                Disclosure saved = disclosureRepository.save(Disclosure.publish(leagueId, stockId,
                        playerId, disclosureTitle, disclosureBody, tradingDay.dayNumber(), now));
                List<Long> imageIds = imageService.attach(leagueId, saved.getId(), stored);
                newsRepository.save(News.requestFrom(saved, now));
                eventPublisher.publishEvent(
                        new DisclosurePublished(saved.getId(), leagueId, stockId, playerId));
                return new PublishedDisclosure(saved.getId(), stockId,
                        saved.getTitle().getValue(), saved.getBody().getValue(),
                        saved.getTradingDay(), saved.getCreatedAt(), imageIds);
            });
        } catch (RuntimeException e) {
            imageService.discard(stored);
            throw e;
        }
    }

    /**
     * 자신이 작성한 공시를 삭제한다.
     *
     * <p>정책 (docs/policies/v1/domain/contents.md "삭제"): 공시 작성자 본인만 삭제할 수 있다. 삭제한 공시와 공시에
     * 달린 댓글은 조회할 수 없지만, 공시 기반 AI 뉴스와 이미 체결된 거래는 유지한다. 그래서 공시 행은 지우지 않고
     * 상태만 바꾸며, 첨부 이미지는 더 이상 볼 수 없으므로 파일까지 지운다 (파일은 커밋 후 삭제). 작성과 달리
     * 리그 상태는 따지지 않아 종료된 리그의 공시도 삭제할 수 있다 (docs/issues/v1/contents.md CNT-03 결정).
     *
     * <p>삭제된 공시의 뉴스도 자동 재시도는 계속하고, 수동 재생성은 막는다 ({@link NewsRegenerationService}).
     *
     * <p>TODO: 댓글 조회 차단(CNT-09 공시 댓글)은 해당 기능을 구현할 때 {@link Disclosure#isDeleted()}로
     *       처리한다.
     *
     * @param memberId 삭제하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @throws NaspiBizException 리그에 입장하지 않았거나, 공시가 없거나(다른 리그·종목, 이미 삭제 포함),
     *         작성자가 아닌 경우
     */
    public void delete(long memberId, long leagueId, long stockId, long disclosureId) {
        // 회원의 플레이어 정보 조회
        long playerId = leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
        // 공시 상태 변경과 이미지 메타데이터 삭제를 한 트랜잭션으로 묶는다
        transactionTemplate.executeWithoutResult(status -> {
            // 경로의 리그·종목에 속한 공시만 삭제 대상으로 본다 (다른 리그·종목의 공시 ID로 지우는 것 방지)
            Disclosure disclosure = disclosureRepository.findById(disclosureId)
                    .filter(d -> d.getLeagueId() == leagueId && d.getStockId() == stockId)
                    .orElseThrow(() -> new NaspiBizException(ErrorCode.DISCLOSURE_NOT_FOUND));
            // 작성자 본인 확인 후 소프트 삭제 (공시 기반 AI 뉴스가 참조하므로 행은 남긴다)
            disclosure.delete(playerId);
            // 삭제한 공시의 이미지는 볼 수 없으므로 메타데이터를 지우고, 파일은 커밋 후 저장소에서 지운다
            imageService.deleteAll(disclosureId);
        });
    }
}
