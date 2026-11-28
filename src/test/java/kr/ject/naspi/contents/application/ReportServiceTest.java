package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import kr.ject.naspi.contents.domain.ImageStorage;
import kr.ject.naspi.contents.domain.News;
import kr.ject.naspi.contents.domain.NewsStatus;
import kr.ject.naspi.contents.domain.NewsType;
import kr.ject.naspi.contents.domain.Report;
import kr.ject.naspi.contents.domain.ReportImage;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueTradingDay;
import kr.ject.naspi.market.application.StockQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({ReportService.class, ImageService.class, ReportServiceTest.Config.class})
class ReportServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};

    private static final long LEAGUE_ID = 3L;
    private static final long REPORTER_MEMBER_ID = 8L;
    private static final long REPORTER_PLAYER_ID = 80L;
    private static final long REPORTER_STOCK_ID = 12L;
    private static final long TARGET_STOCK_ID = 11L;
    private static final long OUTSIDER_MEMBER_ID = 9L;

    @Autowired
    private ReportService reportService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private FailingListener failingListener;

    @MockitoBean
    private ImageStorage imageStorage;

    @MockitoBean
    private LeagueQueryService leagueQueryService;

    @MockitoBean
    private StockQueryService stockQueryService;

    @BeforeEach
    void setUp() {
        // 테스트 컨텍스트를 재사용하므로 실패 설정이 다른 테스트로 새지 않게 되돌린다.
        failingListener.fail = false;
        givenStartedLeague();
    }

    @Test
    @DisplayName("참여자는 다른 참여자의 종목에 제보를 작성하고, 생성 중인 REPORT 뉴스가 함께 저장된다")
    void submit() {
        SubmittedReport result = reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID,
                TARGET_STOCK_ID, "점심에 라면 세 그릇을 드셨습니다.",
                List.of(png("a.png"), png("b.png")));

        Report saved = entityManager.find(Report.class, result.reportId());
        assertThat(saved.getBody().getValue()).isEqualTo("점심에 라면 세 그릇을 드셨습니다.");
        assertThat(saved.getStockId()).isEqualTo(TARGET_STOCK_ID);
        assertThat(saved.getReporterPlayerId()).isEqualTo(REPORTER_PLAYER_ID);
        assertThat(saved.getTradingDay()).isEqualTo(2);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(result.tradingDay()).isEqualTo(2);
        assertThat(reportImages(result.reportId()))
                .extracting(ReportImage::getId, ReportImage::getSortOrder)
                .containsExactly(tuple(result.imageIds().get(0), 0),
                        tuple(result.imageIds().get(1), 1));

        News news = entityManager.find(News.class, result.newsId());
        assertThat(news.getType()).isEqualTo(NewsType.REPORT);
        assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(news.getReportId()).isEqualTo(result.reportId());
        assertThat(news.getDisclosureId()).isNull();
        assertThat(news.getStockId()).isEqualTo(TARGET_STOCK_ID);
        assertThat(news.getTradingDay()).isEqualTo(2);
        assertThat(events.stream(ReportSubmitted.class)).containsExactly(new ReportSubmitted(
                result.reportId(), LEAGUE_ID, TARGET_STOCK_ID, REPORTER_PLAYER_ID));
    }

    @Test
    @DisplayName("자기 종목에는 제보할 수 없다")
    void ownStock() {
        assertBiz(() -> reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID, REPORTER_STOCK_ID,
                        "본문", List.of(png("a.png"))), ErrorCode.REPORT_OWN_STOCK);

        then(imageStorage).shouldHaveNoInteractions();
        assertThat(events.stream(ReportSubmitted.class)).isEmpty();
    }

    @Test
    @DisplayName("진행 중이 아닌 리그(대기실)에서는 제보할 수 없다")
    void waitingLeague() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        assertBiz(() -> reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID, TARGET_STOCK_ID,
                "본문", List.of(png("a.png"))), ErrorCode.REPORT_LEAGUE_NOT_STARTED);
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("종료 시각이 지난 리그에서는 제보할 수 없고 리그 종료로 거절된다")
    void endedLeague() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());
        given(leagueQueryService.requireAccessiblePlayer(REPORTER_MEMBER_ID, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.LEAGUE_ALREADY_ENDED));

        assertBiz(() -> reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID, TARGET_STOCK_ID,
                "본문", List.of()), ErrorCode.LEAGUE_ALREADY_ENDED);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 제보할 수 없다")
    void notJoined() {
        assertBiz(() -> reportService.submit(OUTSIDER_MEMBER_ID, LEAGUE_ID, TARGET_STOCK_ID,
                "본문", List.of()), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("리그에 없는 종목에는 제보할 수 없다")
    void stockNotFound() {
        given(stockQueryService.isListedBy(LEAGUE_ID, 999L, REPORTER_PLAYER_ID))
                .willThrow(new NaspiBizException(ErrorCode.STOCK_NOT_FOUND));

        assertBiz(() -> reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID, 999L, "본문",
                List.of(png("a.png"))), ErrorCode.STOCK_NOT_FOUND);
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("제보 본문은 1~300자로 제한한다")
    void bodyLength() {
        assertThat(reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID, TARGET_STOCK_ID,
                "나".repeat(300), List.of()).body()).hasSize(300);
        assertBiz(() -> reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID, TARGET_STOCK_ID,
                "나".repeat(301), List.of()), ErrorCode.INVALID_REPORT_BODY);
        assertBiz(() -> reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID, TARGET_STOCK_ID,
                " ", List.of()), ErrorCode.INVALID_REPORT_BODY);
    }

    @Test
    @DisplayName("제보 이미지는 선택이며 JPEG·PNG 최대 4개, 장당 50MB 이하만 첨부할 수 있다")
    void imageRules() {
        assertThat(submitWith(List.of()).imageIds()).isEmpty();
        assertThat(submitWith(Collections.nCopies(4, png("a.png"))).imageIds()).hasSize(4);

        assertBiz(() -> submitWith(Collections.nCopies(5, png("a.png"))),
                ErrorCode.IMAGE_COUNT_EXCEEDED);
        byte[] tooLarge = new byte[50 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, tooLarge, 0, PNG.length);
        assertBiz(() -> submitWith(List.of(new ImageFile("a.png", "image/png", tooLarge))),
                ErrorCode.INVALID_IMAGE_SIZE);
        ImageFile gif = new ImageFile("a.gif", "image/gif", "GIF89a".getBytes());
        assertBiz(() -> submitWith(List.of(gif)), ErrorCode.INVALID_IMAGE_FORMAT);
    }

    @Test
    @DisplayName("제보 작성 횟수는 제한하지 않는다 (같은 회원이 같은 종목에 여러 번 작성하면 제보와 뉴스가 각각 생긴다)")
    void unlimited() {
        submitWith(List.of());
        submitWith(List.of());

        assertThat(count("select count(r) from Report r")).isEqualTo(2);
        assertThat(count("select count(n) from News n where n.reportId is not null"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("상장자가 탈퇴한 종목에도 제보할 수 있다")
    void listerWithdrawnStock() {
        // 종목 검증은 제보자 본인이 상장자인지만 보므로, 상장자의 탈퇴 여부와 관계없이 통과해야 한다
        SubmittedReport result = submitWith(List.of());

        assertThat(entityManager.find(Report.class, result.reportId()).getStockId())
                .isEqualTo(TARGET_STOCK_ID);
        assertThat(entityManager.find(News.class, result.newsId()).getStatus())
                .isEqualTo(NewsStatus.PENDING);
        then(leagueQueryService).should(never()).isPlayerWithdrawn(anyLong(), anyLong());
    }

    @Test
    @DisplayName("제보 저장이 실패하면 올린 이미지 파일을 지운다")
    void compensateOnFailure() {
        failingListener.fail = true;

        assertThatThrownBy(() -> submitWith(List.of(png("a.png"), png("b.png"))))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> uploaded = ArgumentCaptor.forClass(String.class);
        then(imageStorage).should(times(2)).upload(uploaded.capture(), any(), anyString());
        uploaded.getAllValues().forEach(key -> then(imageStorage).should().delete(key));
    }

    private SubmittedReport submitWith(List<ImageFile> images) {
        return reportService.submit(REPORTER_MEMBER_ID, LEAGUE_ID, TARGET_STOCK_ID, "본문",
                images);
    }

    private long count(String jpql) {
        return entityManager.getEntityManager().createQuery(jpql, Long.class).getSingleResult();
    }

    private List<ReportImage> reportImages(long reportId) {
        return entityManager.getEntityManager().createQuery(
                        "select i from ReportImage i where i.reportId = :id order by i.sortOrder",
                        ReportImage.class)
                .setParameter("id", reportId)
                .getResultList();
    }

    /** 진행 중인 리그(2거래일차)에 제보자(자기 종목 12 보유), 대상 종목 11, 리그에 없는 회원이 있는 상황을 만든다. */
    private void givenStartedLeague() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.of(
                new LeagueTradingDay(2, 7, NOW.plusHours(23), NOW.minusDays(1),
                        NOW.plusDays(6))));
        given(leagueQueryService.requireAccessiblePlayer(REPORTER_MEMBER_ID, LEAGUE_ID))
                .willReturn(REPORTER_PLAYER_ID);
        given(leagueQueryService.requireAccessiblePlayer(OUTSIDER_MEMBER_ID, LEAGUE_ID))
                .willThrow(new NaspiBizException(ErrorCode.FORBIDDEN));
        given(stockQueryService.isListedBy(LEAGUE_ID, REPORTER_STOCK_ID, REPORTER_PLAYER_ID))
                .willReturn(true);
        given(stockQueryService.isListedBy(LEAGUE_ID, TARGET_STOCK_ID, REPORTER_PLAYER_ID))
                .willReturn(false);
    }

    private static ImageFile png(String filename) {
        return new ImageFile(filename, "image/png", PNG);
    }

    private static void assertBiz(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    /** 제보 작성 이벤트를 받는 쪽이 실패하는 상황을 만들어 제보 저장 트랜잭션 실패를 재현한다. */
    static class FailingListener {

        boolean fail;

        @EventListener
        void on(ReportSubmitted event) {
            if (fail) {
                throw new IllegalStateException("listener failed");
            }
        }
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        }

        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }
}
