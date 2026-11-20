package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureTitle;
import kr.ject.naspi.contents.domain.Image;
import kr.ject.naspi.contents.domain.ImageFormat;
import kr.ject.naspi.contents.domain.ImageStorage;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsGenerationResult;
import kr.ject.naspi.contents.domain.NewsGenerator;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsSource;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.contents.domain.NewsType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ActiveProfiles("test")
@DataJpaTest
@Import({NewsGenerationService.class, ImageService.class, NewsGenerationServiceTest.Config.class})
class NewsGenerationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);

    private static final long LEAGUE_ID = 3L;
    private static final long STOCK_ID = 11L;
    private static final long OTHER_STOCK_ID = 12L;
    private static final long AUTHOR_PLAYER_ID = 70L;

    @Autowired
    private NewsGenerationService newsGenerationService;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private NewsGenerator newsGenerator;

    @MockitoBean
    private ImageStorage imageStorage;

    @Test
    @DisplayName("공시 1건으로 뉴스 1개를 만들고, 제목·본문·호재/악재를 함께 저장해 종목에 연결한다")
    void generate() {
        Disclosure disclosure = disclosure(STOCK_ID, "헬스장 등록", "3개월 끊었습니다.", 2);
        given(newsGenerator.generate(any())).willReturn(generated("헬스 시작", "3개월 등록",
                NewsSentiment.POSITIVE, "헬스장 3개월 등록"));

        newsGenerationService.generate(disclosure.getId());

        News news = news(disclosure.getId());
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(news.getType()).isEqualTo(NewsType.DISCLOSURE);
        assertThat(news.getTitle()).isEqualTo("헬스 시작");
        assertThat(news.getBody()).isEqualTo("3개월 등록");
        assertThat(news.getSentiment()).isEqualTo(NewsSentiment.POSITIVE);
        assertThat(news.getLeagueId()).isEqualTo(LEAGUE_ID);
        assertThat(news.getStockId()).isEqualTo(STOCK_ID);
        assertThat(news.getTradingDay()).isEqualTo(2);
        assertThat(news.getCreatedAt()).isEqualTo(NOW);
        assertThat(entityManager.find(Disclosure.class, disclosure.getId()).getAiSummary())
                .isEqualTo("헬스장 3개월 등록");
    }

    @Test
    @DisplayName("공시 원문과 첨부 이미지, 같은 종목의 과거 공시 요약(삭제된 공시 포함, 오래된 순)을 AI에 넘긴다")
    void generateWithSource() {
        Disclosure first = disclosure(STOCK_ID, "첫 공시", "본문1", 1);
        first.recordAiSummary("요약1");
        Disclosure deleted = disclosure(STOCK_ID, "삭제한 공시", "본문2", 1);
        deleted.recordAiSummary("요약2");
        deleted.delete(AUTHOR_PLAYER_ID);
        // 요약이 없는 공시(미생성·실패)와 다른 종목의 공시는 넘기지 않는다
        disclosure(STOCK_ID, "미생성 공시", "본문3", 1);
        disclosure(OTHER_STOCK_ID, "다른 종목", "본문4", 1).recordAiSummary("다른 종목 요약");
        Disclosure target = disclosure(STOCK_ID, "새 공시", "새 본문", 2);
        entityManager.persist(Image.attach(LEAGUE_ID, target.getId(), 0, "key-0", "a.png",
                ImageFormat.PNG, 10, NOW));
        entityManager.flush();
        given(imageStorage.signedUrl(anyString(), any()))
                .willAnswer(invocation -> "https://signed/" + invocation.getArgument(0));
        given(newsGenerator.generate(any())).willReturn(
                new NewsGenerationResult.Skipped("사실 없음"));

        newsGenerationService.generate(target.getId());

        ArgumentCaptor<NewsSource> source = ArgumentCaptor.forClass(NewsSource.class);
        then(newsGenerator).should().generate(source.capture());
        assertThat(source.getValue().title()).isEqualTo("새 공시");
        assertThat(source.getValue().body()).isEqualTo("새 본문");
        assertThat(source.getValue().imageUrls()).containsExactly("https://signed/key-0");
        assertThat(source.getValue().pastSummaries()).containsExactly("요약1", "요약2");
    }

    @Test
    @DisplayName("공시에 기사로 쓸 사실이 없다고 AI가 판단하면 뉴스를 발행하지 않고 미생성 사유를 공시에 남기며, 미생성은 재시도하지 않는다")
    void skipped() {
        Disclosure disclosure = disclosure(STOCK_ID, "ㅁㄴㅇㄹ", "ㅋㅋㅋ", 2);
        given(newsGenerator.generate(any())).willReturn(
                new NewsGenerationResult.Skipped("사실 없음"));

        newsGenerationService.generate(disclosure.getId());

        then(newsGenerator).should().generate(any());

        News news = news(disclosure.getId());
        assertThat(news.getStatus()).isEqualTo(NewsStatus.SKIPPED);
        assertThat(news.getTitle()).isNull();
        assertThat(news.getBody()).isNull();
        Disclosure kept = entityManager.find(Disclosure.class, disclosure.getId());
        assertThat(kept.getAiSummary()).isNull();
        assertThat(kept.getAiSkipReason()).isEqualTo("사실 없음");
    }

    @Test
    @DisplayName("자동 재시도까지 모두 실패하면 수동 재시도할 수 있는 실패 상태로 남기고, 공시 원문은 유지한다")
    void failed() {
        Disclosure disclosure = disclosure(STOCK_ID, "헬스장 등록", "3개월 끊었습니다.", 2);
        given(newsGenerator.generate(any())).willReturn(
                new NewsGenerationResult.Failed("timeout"));

        newsGenerationService.generate(disclosure.getId());

        assertThat(news(disclosure.getId()).getStatus()).isEqualTo(NewsStatus.FAILED);
        Disclosure kept = entityManager.find(Disclosure.class, disclosure.getId());
        assertThat(kept.isDeleted()).isFalse();
        assertThat(kept.getTitle().getValue()).isEqualTo("헬스장 등록");
    }

    @Test
    @DisplayName("이미지 URL 발급이 실패해도 뉴스는 실패 상태로 남아 수동 재시도할 수 있다")
    void failedBeforeGeneration() {
        Disclosure disclosure = disclosure(STOCK_ID, "헬스장 등록", "3개월 끊었습니다.", 2);
        entityManager.persist(Image.attach(LEAGUE_ID, disclosure.getId(), 0, "key-0", "a.png",
                ImageFormat.PNG, 10, NOW));
        given(imageStorage.signedUrl(anyString(), any()))
                .willThrow(new IllegalStateException("signBlob 실패"));

        newsGenerationService.generate(disclosure.getId());

        assertThat(news(disclosure.getId()).getStatus()).isEqualTo(NewsStatus.FAILED);
        then(newsGenerator).should(never()).generate(any());
    }

    @Test
    @DisplayName("삭제된 공시도 뉴스 생성을 진행한다")
    void generateForDeletedDisclosure() {
        Disclosure disclosure = disclosure(STOCK_ID, "헬스장 등록", "3개월 끊었습니다.", 2);
        disclosure.delete(AUTHOR_PLAYER_ID);
        given(newsGenerator.generate(any())).willReturn(generated("헬스 시작", "3개월 등록",
                NewsSentiment.POSITIVE, "요약"));

        newsGenerationService.generate(disclosure.getId());

        assertThat(news(disclosure.getId()).getStatus()).isEqualTo(NewsStatus.PUBLISHED);
    }

    @Test
    @DisplayName("공시 1건에 뉴스는 1개만 만든다 (결과가 반영된 뉴스는 다시 요청해도 생성하지 않는다)")
    void generateOnce() {
        Disclosure disclosure = disclosure(STOCK_ID, "헬스장 등록", "3개월 끊었습니다.", 2);
        given(newsGenerator.generate(any())).willReturn(generated("헬스 시작", "3개월 등록",
                NewsSentiment.NEGATIVE, "요약"));

        newsGenerationService.generate(disclosure.getId());
        newsGenerationService.generate(disclosure.getId());

        then(newsGenerator).should().generate(any());
        Long count = entityManager.getEntityManager()
                .createQuery("select count(n) from News n where n.disclosureId = :id", Long.class)
                .setParameter("id", disclosure.getId())
                .getSingleResult();
        assertThat(count).isEqualTo(1);
        assertThat(news(disclosure.getId()).getSentiment()).isEqualTo(NewsSentiment.NEGATIVE);
    }

    @Test
    @DisplayName("공시와 함께 만든 생성 중 뉴스가 없으면 AI를 호출하지 않는다")
    void noPendingNews() {
        Disclosure disclosure = entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID,
                STOCK_ID, AUTHOR_PLAYER_ID, DisclosureTitle.of("제목"), DisclosureBody.of("본문"),
                2, NOW));

        newsGenerationService.generate(disclosure.getId());

        then(newsGenerator).should(never()).generate(any());
    }

    @Test
    @DisplayName("뉴스 생성이 공시 저장에 영향을 주지 않도록 공시 저장 커밋 후 비동기로 시작한다")
    void listenerRunsAfterCommitAsync() throws NoSuchMethodException {
        Method method = DisclosurePublishedListener.class.getMethod("onDisclosurePublished",
                DisclosurePublished.class);

        assertThat(method.isAnnotationPresent(Async.class)).isTrue();
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    /** 공시 작성과 같이 공시와 생성 중(PENDING) 뉴스를 함께 저장한다. */
    private Disclosure disclosure(long stockId, String title, String body, int tradingDay) {
        Disclosure disclosure = entityManager.persistAndFlush(Disclosure.publish(LEAGUE_ID,
                stockId, AUTHOR_PLAYER_ID, DisclosureTitle.of(title), DisclosureBody.of(body),
                tradingDay, NOW));
        entityManager.persistAndFlush(News.requestFrom(disclosure, NOW));
        return disclosure;
    }

    private News news(long disclosureId) {
        return entityManager.getEntityManager()
                .createQuery("select n from News n where n.disclosureId = :id", News.class)
                .setParameter("id", disclosureId)
                .getSingleResult();
    }

    private static NewsGenerationResult.Generated generated(String title, String body,
                                                            NewsSentiment sentiment,
                                                            String summary) {
        return new NewsGenerationResult.Generated(title, body, sentiment, summary);
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        }
    }
}
