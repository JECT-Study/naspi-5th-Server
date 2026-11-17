package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.contents.domain.DisclosureTitle;
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
 * 공시 작성.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): 상장자는 자기 종목에 공시를 작성할 수 있고 작성 횟수는
 * 제한하지 않는다. 제목 1~30자, 본문 1~300자, 이미지는 선택이며 JPEG·PNG로 최대 4개다. 작성을 마치면 공시 기반
 * AI 뉴스 생성을 요청한다 ({@link DisclosurePublished}). 공시는 진행 중인 리그에서만 작성할 수 있다
 * (docs/issues/v1/contents.md CNT-01 결정).
 *
 * <p>이미지 파일은 GCS 같은 외부 저장소에 올리므로, 그동안 DB 트랜잭션을 열어 두지 않는다. 검증 → 파일 업로드
 * (트랜잭션 밖) → 공시·이미지 메타데이터 저장과 이벤트 발행(한 트랜잭션) 순서로 처리하고, 저장이 실패하면 올린
 * 파일을 지운다. 그래서 이 서비스는 호출하는 쪽의 트랜잭션 안에서 부르면 안 된다.
 */
@Service
public class DisclosureService {

    private final DisclosureRepository disclosureRepository;
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
     * @param imageService 이미지 서비스
     * @param leagueQueryService 리그 상태·플레이어 조회
     * @param stockQueryService 종목 상장자 조회
     * @param eventPublisher 공시 게시 이벤트 발행
     * @param transactionManager 공시 저장 트랜잭션
     * @param clock 현재 시각
     */
    public DisclosureService(DisclosureRepository disclosureRepository,
                             ImageService imageService, LeagueQueryService leagueQueryService,
                             StockQueryService stockQueryService,
                             ApplicationEventPublisher eventPublisher,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.disclosureRepository = disclosureRepository;
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
                Disclosure saved = disclosureRepository.save(Disclosure.publish(leagueId, stockId,
                        playerId, disclosureTitle, disclosureBody, tradingDay.dayNumber(),
                        LocalDateTime.now(clock)));
                List<Long> imageIds = imageService.attach(leagueId, saved.getId(), stored);
                // TODO: 이 이벤트를 받아 AI 뉴스를 생성하는 구독자는 아직 없다. AI 제공자(GLB-06)가 정해지면
                //       CNT-05에서 구현한다. 그 전까지 공시는 저장되지만 뉴스는 생성되지 않는다.
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
}
