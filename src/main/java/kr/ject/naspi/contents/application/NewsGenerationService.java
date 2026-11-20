package kr.ject.naspi.contents.application;

import java.util.List;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureRepository;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsGenerationResult;
import kr.ject.naspi.contents.domain.NewsGenerator;
import kr.ject.naspi.contents.domain.NewsRepository;
import kr.ject.naspi.contents.domain.NewsSource;
import kr.ject.naspi.contents.domain.NewsStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공시 기반 AI 뉴스 생성. 생성 중(PENDING) 뉴스를 AI 결과로 채워 발행·미생성·실패 중 하나로 만든다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "뉴스 - 공시 기반 AI 뉴스"): 실패는 최대 2회 자동 재시도하고
 * ({@link NewsGenerator}), 미생성은 재시도하지 않는다. 생성 결과와 관계없이 공시는 유지된다.
 *
 * <p>AI 호출 동안 DB 트랜잭션을 열어 두지 않으므로, 호출하는 쪽의 트랜잭션 안에서 부르면 안 된다.
 *
 * <p>TODO: PENDING에 멈춘 뉴스 복구 (CNT-07). 커밋 직후 서버가 종료되면 뉴스가 PENDING으로 남을 수 있다.
 */
@Slf4j
@Service
public class NewsGenerationService {

    private final NewsRepository newsRepository;
    private final DisclosureRepository disclosureRepository;
    private final ImageService imageService;
    private final NewsGenerator newsGenerator;
    private final TransactionTemplate transactionTemplate;

    /**
     * 뉴스 생성 서비스를 만든다.
     *
     * @param newsRepository 뉴스 저장소
     * @param disclosureRepository 공시 저장소
     * @param imageService 공시 이미지 URL 발급
     * @param newsGenerator AI 뉴스 생성기
     * @param transactionManager 뉴스 저장 트랜잭션
     */
    public NewsGenerationService(NewsRepository newsRepository,
                                 DisclosureRepository disclosureRepository,
                                 ImageService imageService, NewsGenerator newsGenerator,
                                 PlatformTransactionManager transactionManager) {
        this.newsRepository = newsRepository;
        this.disclosureRepository = disclosureRepository;
        this.imageService = imageService;
        this.newsGenerator = newsGenerator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 공시의 생성 중(PENDING) 뉴스를 AI로 채우고 상태를 갱신한다. 생성 중인 뉴스가 없으면 아무것도 하지 않는다.
     *
     * @param disclosureId 공시 ID
     */
    public void generate(long disclosureId) {
        Request request = transactionTemplate.execute(status -> prepare(disclosureId));
        if (request == null) {
            return;
        }

        // AI 뉴스 생성 시도
        NewsGenerationResult result = callGenerator(disclosureId, request);

        transactionTemplate.executeWithoutResult(status -> complete(disclosureId, result));
    }

    /**
     * AI에 넘길 공시 내용을 모은다. 생성 중인 뉴스가 없으면 null.
     */
    private Request prepare(long disclosureId) {
        News news = newsRepository.findByDisclosureId(disclosureId).orElse(null);
        if (news == null) {
            log.warn("공시에 생성 중 뉴스가 없음: disclosureId={}", disclosureId);
            return null;
        }
        if (news.getStatus() != NewsStatus.PENDING) {
            log.info("이미 결과가 반영된 뉴스: disclosureId={}, status={}", disclosureId,
                    news.getStatus());
            return null;
        }
        Disclosure disclosure = disclosureRepository.findById(disclosureId).orElseThrow();  // 공시 정보 조회
        List<String> pastSummaries = disclosureRepository.findAiSummariesBefore(            // 공시 요약 정보 조회
                disclosure.getLeagueId(), disclosure.getStockId(), disclosureId);
        return new Request(disclosure.getTitle().getValue(), disclosure.getBody().getValue(),
                pastSummaries);
    }

    /**
     * AI로 뉴스를 생성한다. 이미지 URL 발급이 실패하면 실패 결과를 돌려준다.
     */
    private NewsGenerationResult callGenerator(long disclosureId, Request request) {
        // 01. 공시 이미지 조회
        List<String> imageUrls;
        try {
            // AI 제공자가 이미지를 직접 내려받는 주소
            imageUrls = imageService.issueUrls(disclosureId).stream()
                    .map(ImageUrl::signedUrl)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("AI 뉴스 생성 준비 실패: disclosureId={}", disclosureId, e);
            return new NewsGenerationResult.Failed(e.getMessage());
        }

        // 02. 뉴스 생성 시도
        return newsGenerator.generate(new NewsSource(request.title(), request.body(),
                imageUrls, request.pastSummaries()));
    }

    private void complete(long disclosureId, NewsGenerationResult result) {
        News news = newsRepository.findByDisclosureId(disclosureId).orElseThrow();
        news.complete(result);
        switch (result) {
            case NewsGenerationResult.Generated generated -> {
                // 같은 종목의 다음 뉴스 생성에 과거 맥락으로 쓴다
                disclosureRepository.findById(disclosureId).orElseThrow()
                        .recordAiSummary(generated.summary());
                log.info("AI 뉴스 발행: disclosureId={}, newsId={}, sentiment={}", disclosureId,
                        news.getId(), generated.sentiment());
            }
            case NewsGenerationResult.Skipped skipped -> {
                disclosureRepository.findById(disclosureId).orElseThrow()
                        .recordAiSkipReason(skipped.reason());
                log.info("AI 뉴스 미생성: disclosureId={}, reason={}", disclosureId,
                        skipped.reason());
            }
            case NewsGenerationResult.Failed failed ->
                    log.warn("AI 뉴스 생성 실패, 수동 재시도 대상: disclosureId={}, reason={}",
                            disclosureId, failed.reason());
        }
    }

    /** AI에 넘길 공시 내용. */
    private record Request(String title, String body, List<String> pastSummaries) {
    }
}
