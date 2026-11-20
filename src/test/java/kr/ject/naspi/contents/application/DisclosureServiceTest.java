package kr.ject.naspi.contents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import kr.ject.naspi.contents.domain.Disclosure;
import kr.ject.naspi.contents.domain.DisclosureBody;
import kr.ject.naspi.contents.domain.DisclosureStatus;
import kr.ject.naspi.contents.domain.DisclosureTitle;
import kr.ject.naspi.contents.domain.Image;
import kr.ject.naspi.contents.domain.ImageStorage;
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
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({DisclosureService.class, ImageService.class, DisclosureServiceTest.Config.class})
class DisclosureServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

    private static final long LEAGUE_ID = 3L;
    private static final long HOST_MEMBER_ID = 7L;
    private static final long HOST_PLAYER_ID = 70L;
    private static final long HOST_STOCK_ID = 11L;
    private static final long OTHER_MEMBER_ID = 8L;
    private static final long OTHER_PLAYER_ID = 80L;
    private static final long OUTSIDER_MEMBER_ID = 9L;

    @Autowired
    private DisclosureService disclosureService;

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
    void resetListener() {
        // 테스트 컨텍스트를 재사용하므로 실패 설정이 다른 테스트로 새지 않게 되돌린다.
        failingListener.fail = false;
    }

    @Test
    @DisplayName("상장자는 진행 중인 리그에서 자기 종목에 이미지와 함께 공시를 작성하고, 작성한 거래일을 기록한다")
    void publish() {
        League league = startedLeague();

        PublishedDisclosure result = disclosureService.publish(league.hostMemberId,
                league.leagueId, league.hostStockId, "헬스장 등록", "3개월 끊었습니다.",
                List.of(png("a.png"), new ImageFile("b.jpeg", "image/jpeg", JPEG)));

        Disclosure saved = entityManager.find(Disclosure.class, result.disclosureId());
        assertThat(saved.getTitle().getValue()).isEqualTo("헬스장 등록");
        assertThat(saved.getBody().getValue()).isEqualTo("3개월 끊었습니다.");
        assertThat(saved.getStockId()).isEqualTo(league.hostStockId);
        assertThat(saved.getAuthorPlayerId()).isEqualTo(league.hostPlayerId);
        assertThat(saved.getStatus()).isEqualTo(DisclosureStatus.PUBLISHED);
        assertThat(saved.getTradingDay()).isEqualTo(2);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(images(result.disclosureId()))
                .extracting(Image::getId, Image::getSortOrder, Image::getContentType,
                        Image::getLeagueId)
                .containsExactly(
                        tuple(result.imageIds().get(0), 0, "image/png", league.leagueId),
                        tuple(result.imageIds().get(1), 1, "image/jpeg", league.leagueId));
        then(imageStorage).should(times(2)).upload(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("공시 작성을 마치면 공시 기반 AI 뉴스 생성을 요청한다 (공시 게시 이벤트)")
    void publishEvent() {
        League league = startedLeague();

        PublishedDisclosure result = disclosureService.publish(league.hostMemberId,
                league.leagueId, league.hostStockId, "제목", "본문", List.of());

        assertThat(events.stream(DisclosurePublished.class)).containsExactly(
                new DisclosurePublished(result.disclosureId(), league.leagueId,
                        league.hostStockId, league.hostPlayerId));
    }

    @Test
    @DisplayName("이미지 첨부는 선택 사항이다")
    void publishWithoutImages() {
        League league = startedLeague();

        PublishedDisclosure result = disclosureService.publish(league.hostMemberId,
                league.leagueId, league.hostStockId, "제목", "본문", List.of());

        assertThat(result.imageIds()).isEmpty();
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일 공시 작성 횟수는 제한하지 않는다")
    void publishUnlimited() {
        League league = startedLeague();

        for (int i = 0; i < 20; i++) {
            disclosureService.publish(league.hostMemberId, league.leagueId, league.hostStockId,
                    "제목" + i, "본문", List.of());
        }

        assertThat(events.stream(DisclosurePublished.class)).hasSize(20);
    }

    @Test
    @DisplayName("작성이 완료된 공시는 수정할 수 없다 (제목·본문·작성 정보가 저장 후 바뀌지 않는다)")
    void publishedDisclosureIsImmutable() throws Exception {
        League league = startedLeague();
        PublishedDisclosure result = disclosureService.publish(league.hostMemberId,
                league.leagueId, league.hostStockId, "원래 제목", "원래 본문", List.of());
        entityManager.flush();
        entityManager.clear();

        // 공시 수정 API·메서드가 없으므로, 영속 상태의 필드를 강제로 바꿔도 DB에 반영되지 않는지 확인한다.
        Disclosure loaded = entityManager.find(Disclosure.class, result.disclosureId());
        ReflectionTestUtils.setField(loaded, "title", DisclosureTitle.of("바꾼 제목"));
        ReflectionTestUtils.setField(loaded, "body", DisclosureBody.of("바꾼 본문"));
        ReflectionTestUtils.setField(loaded, "tradingDay", 5);
        entityManager.flush();
        entityManager.clear();

        Disclosure reloaded = entityManager.find(Disclosure.class, result.disclosureId());
        assertThat(reloaded.getTitle().getValue()).isEqualTo("원래 제목");
        assertThat(reloaded.getBody().getValue()).isEqualTo("원래 본문");
        assertThat(reloaded.getTradingDay()).isEqualTo(2);
        assertThat(Disclosure.class.getMethods())
                .extracting(Method::getName)
                .noneMatch(name -> name.startsWith("set") || name.startsWith("update")
                        || name.startsWith("edit") || name.startsWith("change"));
    }

    @Test
    @DisplayName("공시 제목은 1~30자, 본문은 1~300자로 제한한다")
    void titleAndBodyLength() {
        League league = startedLeague();
        String title30 = "가".repeat(30);
        String body300 = "나".repeat(300);

        assertThat(disclosureService.publish(league.hostMemberId, league.leagueId,
                league.hostStockId, title30, body300, List.of()).title()).isEqualTo(title30);
        assertBiz(() -> publishAsHost(league, "가".repeat(31), "본문"),
                ErrorCode.INVALID_DISCLOSURE_TITLE);
        assertBiz(() -> publishAsHost(league, " ", "본문"), ErrorCode.INVALID_DISCLOSURE_TITLE);
        assertBiz(() -> publishAsHost(league, null, "본문"), ErrorCode.INVALID_DISCLOSURE_TITLE);
        assertBiz(() -> publishAsHost(league, "제목", "나".repeat(301)),
                ErrorCode.INVALID_DISCLOSURE_BODY);
        assertBiz(() -> publishAsHost(league, "제목", ""), ErrorCode.INVALID_DISCLOSURE_BODY);
    }

    @Test
    @DisplayName("이미지는 JPEG, PNG만 최대 4개까지 첨부할 수 있고, 어기면 공시를 저장하지 않는다")
    void imageRules() {
        League league = startedLeague();

        assertBiz(() -> disclosureService.publish(league.hostMemberId, league.leagueId,
                        league.hostStockId, "제목", "본문", Collections.nCopies(5, png("a.png"))),
                ErrorCode.IMAGE_COUNT_EXCEEDED);
        assertBiz(() -> disclosureService.publish(league.hostMemberId, league.leagueId,
                        league.hostStockId, "제목", "본문",
                        List.of(new ImageFile("a.gif", "image/gif", "GIF89a".getBytes()))),
                ErrorCode.INVALID_IMAGE_FORMAT);
        assertThat(events.stream(DisclosurePublished.class)).isEmpty();
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("자신이 상장하지 않은 종목에는 공시를 작성할 수 없다")
    void notOwnedStock() {
        League league = startedLeague();

        assertBiz(() -> disclosureService.publish(league.otherMemberId, league.leagueId,
                        league.hostStockId, "제목", "본문", List.of(png("a.png"))),
                ErrorCode.DISCLOSURE_STOCK_NOT_OWNED);
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 공시를 작성할 수 없다")
    void notJoined() {
        League league = startedLeague();

        assertBiz(() -> disclosureService.publish(OUTSIDER_MEMBER_ID, league.leagueId,
                league.hostStockId, "제목", "본문", List.of()), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("공시는 진행 중인 리그에서만 작성할 수 있다 (대기실에서는 작성 불가)")
    void waitingLeague() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        assertBiz(() -> disclosureService.publish(HOST_MEMBER_ID, LEAGUE_ID, HOST_STOCK_ID,
                "제목", "본문", List.of(png("a.png"))), ErrorCode.DISCLOSURE_LEAGUE_NOT_STARTED);
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공시 저장이 실패하면 이미 올린 이미지 파일을 지운다")
    void compensateOnFailure() {
        League league = startedLeague();
        failingListener.fail = true;

        assertThatThrownBy(() -> disclosureService.publish(league.hostMemberId, league.leagueId,
                league.hostStockId, "제목", "본문", List.of(png("a.png"), png("b.png"))))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> uploaded = ArgumentCaptor.forClass(String.class);
        then(imageStorage).should(times(2)).upload(uploaded.capture(), any(), anyString());
        uploaded.getAllValues().forEach(key -> then(imageStorage).should().delete(key));
    }

    @Test
    @DisplayName("이미지 업로드가 실패하면 공시를 저장하지 않는다")
    void uploadFailure() {
        League league = startedLeague();
        willThrow(new IllegalStateException("gcs down"))
                .given(imageStorage).upload(anyString(), any(), anyString());

        assertThatThrownBy(() -> publishAsHost(league, "제목", "본문", png("a.png")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(events.stream(DisclosurePublished.class)).isEmpty();
        assertThat(entityManager.getEntityManager()
                .createQuery("select count(d) from Disclosure d", Long.class)
                .getSingleResult()).isZero();
        then(imageStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("공시 작성자는 자신의 공시를 삭제할 수 있고, 삭제한 공시는 조회 대상에서 빠지며 첨부 이미지도 지운다")
    void delete() {
        League league = startedLeague();
        PublishedDisclosure result = publishAsHost(league, "제목", "본문", png("a.png"));

        disclosureService.delete(league.hostMemberId, league.leagueId, league.hostStockId,
                result.disclosureId());

        entityManager.flush();
        entityManager.clear();
        Disclosure deleted = entityManager.find(Disclosure.class, result.disclosureId());
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getStatus()).isEqualTo(DisclosureStatus.DELETED);
        assertThat(images(result.disclosureId())).isEmpty();
        // 파일은 커밋된 뒤에 지운다 (ImageServiceTest). 테스트 트랜잭션은 커밋하지 않으므로 아직 남아 있어야 한다.
        then(imageStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("공시를 삭제해도 공시 행은 남긴다 (공시 기반 AI 뉴스는 삭제 후에도 유지)")
    void deleteKeepsRow() {
        League league = startedLeague();
        PublishedDisclosure result = publishAsHost(league, "제목", "본문");

        disclosureService.delete(league.hostMemberId, league.leagueId, league.hostStockId,
                result.disclosureId());

        entityManager.clear();
        Disclosure deleted = entityManager.find(Disclosure.class, result.disclosureId());
        assertThat(deleted).isNotNull();
        assertThat(deleted.getTitle().getValue()).isEqualTo("제목");
    }

    @Test
    @DisplayName("종료된 리그의 공시도 작성자는 삭제할 수 있다")
    void deleteInEndedLeague() {
        League league = startedLeague();
        PublishedDisclosure result = publishAsHost(league, "제목", "본문");
        // 리그가 끝나면 현재 거래일이 없다
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.empty());

        disclosureService.delete(league.hostMemberId, league.leagueId, league.hostStockId,
                result.disclosureId());

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(Disclosure.class, result.disclosureId()).isDeleted())
                .isTrue();
    }

    @Test
    @DisplayName("공시 작성자 본인만 삭제할 수 있다 (같은 리그의 다른 참여자는 삭제 불가)")
    void deleteByOtherPlayer() {
        League league = startedLeague();
        PublishedDisclosure result = publishAsHost(league, "제목", "본문", png("a.png"));

        assertBiz(() -> disclosureService.delete(league.otherMemberId, league.leagueId,
                league.hostStockId, result.disclosureId()), ErrorCode.DISCLOSURE_NOT_AUTHOR);

        entityManager.clear();
        assertThat(entityManager.find(Disclosure.class, result.disclosureId()).isDeleted())
                .isFalse();
        assertThat(images(result.disclosureId())).hasSize(1);
        then(imageStorage).should(never()).delete(anyString());
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 공시를 삭제할 수 없다")
    void deleteByOutsider() {
        League league = startedLeague();
        PublishedDisclosure result = publishAsHost(league, "제목", "본문");

        assertBiz(() -> disclosureService.delete(OUTSIDER_MEMBER_ID, league.leagueId,
                league.hostStockId, result.disclosureId()), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("이미 삭제했거나 없는 공시, 다른 종목의 공시는 삭제할 수 없다")
    void deleteNotFound() {
        League league = startedLeague();
        PublishedDisclosure result = publishAsHost(league, "제목", "본문");

        assertBiz(() -> disclosureService.delete(league.hostMemberId, league.leagueId,
                league.hostStockId + 1, result.disclosureId()), ErrorCode.DISCLOSURE_NOT_FOUND);
        assertBiz(() -> disclosureService.delete(league.hostMemberId, league.leagueId,
                league.hostStockId, 999_999L), ErrorCode.DISCLOSURE_NOT_FOUND);

        disclosureService.delete(league.hostMemberId, league.leagueId, league.hostStockId,
                result.disclosureId());
        assertBiz(() -> disclosureService.delete(league.hostMemberId, league.leagueId,
                league.hostStockId, result.disclosureId()), ErrorCode.DISCLOSURE_NOT_FOUND);
    }

    private PublishedDisclosure publishAsHost(League league, String title, String body,
                                              ImageFile... images) {
        return disclosureService.publish(league.hostMemberId, league.leagueId,
                league.hostStockId, title, body, List.of(images));
    }

    /** 진행 중인 리그(2거래일차)에 방장(상장자)과 다른 참여자, 리그에 없는 회원이 있는 상황을 만든다. */
    private League startedLeague() {
        given(leagueQueryService.findCurrentTradingDay(LEAGUE_ID)).willReturn(Optional.of(
                new LeagueTradingDay(2, 7, NOW.plusHours(23), NOW.minusDays(1),
                        NOW.plusDays(6))));
        given(leagueQueryService.findPlayerId(HOST_MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.of(HOST_PLAYER_ID));
        given(leagueQueryService.findPlayerId(OTHER_MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.of(OTHER_PLAYER_ID));
        given(leagueQueryService.findPlayerId(OUTSIDER_MEMBER_ID, LEAGUE_ID))
                .willReturn(Optional.empty());
        given(stockQueryService.isListedBy(LEAGUE_ID, HOST_STOCK_ID, HOST_PLAYER_ID))
                .willReturn(true);
        given(stockQueryService.isListedBy(LEAGUE_ID, HOST_STOCK_ID, OTHER_PLAYER_ID))
                .willReturn(false);
        return new League(LEAGUE_ID, HOST_MEMBER_ID, HOST_PLAYER_ID, HOST_STOCK_ID,
                OTHER_MEMBER_ID);
    }

    private List<Image> images(long disclosureId) {
        return entityManager.getEntityManager().createQuery(
                        "select i from Image i where i.disclosureId = :id order by i.sortOrder",
                        Image.class)
                .setParameter("id", disclosureId)
                .getResultList();
    }

    private static ImageFile png(String filename) {
        return new ImageFile(filename, "image/png", PNG);
    }

    private static void assertBiz(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    private record League(long leagueId, long hostMemberId, long hostPlayerId, long hostStockId,
                          long otherMemberId) {
    }

    /** 공시 게시 이벤트를 받는 쪽이 실패하는 상황을 만들어 공시 저장 트랜잭션 실패를 재현한다. */
    static class FailingListener {

        boolean fail;

        @EventListener
        void on(DisclosurePublished event) {
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
