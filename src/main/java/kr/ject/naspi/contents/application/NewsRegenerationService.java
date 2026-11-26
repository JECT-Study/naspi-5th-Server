package kr.ject.naspi.contents.application;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실패한 AI 뉴스의 수동 재생성 요청.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 공시 기반 AI 뉴스"): 자동 재시도까지 모두 실패하면
 * 공시 작성자가 수동으로 다시 생성할 수 있다. 공시가 삭제되지 않았고 리그가 진행 중일 때만 가능하다. 결정
 * (docs/issues/v1/contents.md CNT-07): 뉴스당 1분에 한 번만 요청할 수 있다.
 *
 * <p>요청은 뉴스를 생성 중(PENDING)으로 되돌리고, 커밋 후 비동기로 {@link NewsGenerationService}가 생성한다
 * (자동 재시도 포함). 같은 뉴스의 동시 요청은 비관적 락으로 직렬화해 AI를 두 번 부르지 않는다.
 */
@Service
public class NewsRegenerationService {

    private final NewsRepository newsRepository;
    private final DisclosureRepository disclosureRepository;
    private final LeagueQueryService leagueQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 뉴스 재생성 서비스를 만든다.
     *
     * @param newsRepository 뉴스 저장소
     * @param disclosureRepository 공시 저장소
     * @param leagueQueryService 리그 상태·플레이어 조회
     * @param eventPublisher 재생성 요청 이벤트 발행
     * @param transactionManager 요청 저장 트랜잭션
     * @param clock 현재 시각
     */
    public NewsRegenerationService(NewsRepository newsRepository,
                                   DisclosureRepository disclosureRepository,
                                   LeagueQueryService leagueQueryService,
                                   ApplicationEventPublisher eventPublisher,
                                   PlatformTransactionManager transactionManager, Clock clock) {
        this.newsRepository = newsRepository;
        this.disclosureRepository = disclosureRepository;
        this.leagueQueryService = leagueQueryService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 공시의 실패한 뉴스를 다시 생성하도록 요청한다.
     *
     * @param memberId 요청하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param disclosureId 공시 ID
     * @return 재생성을 요청한 뉴스 (생성 중 상태)
     * @throws NaspiBizException 리그가 진행 중이 아니거나, 리그에 입장하지 않았거나, 공시가 없거나 삭제됐거나,
     *         작성자가 아니거나, 뉴스가 없거나 실패한 뉴스가 아니거나, 1분 안에 다시 요청한 경우
     */
    public RegeneratedNews regenerate(long memberId, long leagueId, long stockId,
                                      long disclosureId) {
        // 리그가 없으면 여기서 오류
        if (leagueQueryService.findCurrentTradingDay(leagueId).isEmpty()) {
            throw new NaspiBizException(ErrorCode.NEWS_REGENERATION_LEAGUE_NOT_STARTED);
        }
        long playerId = leagueQueryService.requireAccessiblePlayer(memberId, leagueId);
        return transactionTemplate.execute(status -> {
            // 삭제된 공시는 수동 재생성 불가 (자동 재시도는 삭제와 무관하게 계속된다)
            Disclosure disclosure = disclosureRepository.findById(disclosureId)
                    .filter(d -> d.getLeagueId() == leagueId && d.getStockId() == stockId)
                    .filter(d -> !d.isDeleted())
                    .orElseThrow(() -> new NaspiBizException(ErrorCode.DISCLOSURE_NOT_FOUND));
            if (disclosure.getAuthorPlayerId() != playerId) {
                throw new NaspiBizException(ErrorCode.NEWS_REGENERATION_NOT_AUTHOR);
            }
            News news = newsRepository.findWithLockByDisclosureId(disclosureId)
                    .orElseThrow(() -> new NaspiBizException(ErrorCode.NEWS_NOT_FOUND));
            news.requestRegeneration(LocalDateTime.now(clock));
            eventPublisher.publishEvent(
                    new NewsRegenerationRequested(news.getId(), disclosureId));
            return new RegeneratedNews(news.getId(), news.getStatus());
        });
    }
}
