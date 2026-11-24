package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.asset.application.InitialAssetAllocationService;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockListingService;
import kr.ject.naspi.market.application.StockOpeningService;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import kr.ject.naspi.member.application.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueStartUseCase.class,
        StockOpeningService.class, LeagueService.class, LeagueCreationUseCase.class,
        InviteCodeIssuer.class, LeagueJoinLimit.class, StockListingService.class,
        StockQueryService.class, LocalStockSnapshotCache.class,
        InitialAssetAllocationService.class, MutableClock.Config.class, LeagueQueryService.class,
        MemberService.class})
class LeagueStartUseCaseTest {

    private static final long HOST_MEMBER_ID = 1L;

    @Autowired
    private StockListingService stockListingService;

    @Autowired
    private LeagueStartUseCase useCaseUnderTest;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private StockQueryService stockQueryService;

    @Autowired
    private LeagueCreationUseCase useCase;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MutableClock clock;

    private long nextMemberId = 100L;

    @Test
    @DisplayName("방장은 참여자가 3명 이상이고 대기자가 없으면 리그를 시작할 수 있다")
    void start() {
        CreatedLeague created = leagueWithParticipants(3);

        StartedLeague started = useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime endsAt = now.toLocalDate().plusDays(7).atTime(9, 0);
        assertThat(started).isEqualTo(new StartedLeague(created.leagueId(), now, endsAt));
        League league = entityManager.find(League.class, created.leagueId());
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
        assertThat(league.getStartedAt()).isEqualTo(now);
        assertThat(league.getEndsAt()).isEqualTo(endsAt);
        assertThat(league.getInviteCode()).isNotNull();
    }

    @Test
    @DisplayName("대기자가 상장해 참여자 3명·대기자 0명이 되면 방장이 리그를 시작할 수 있다")
    void startAfterWaitingPlayersList() {
        CreatedLeague created = useCase.create(HOST_MEMBER_ID, "리그", "나스피", "소개");
        long first = nextMemberId++;
        long second = nextMemberId++;
        leagueService.join(first, created.inviteCode());
        leagueService.join(second, created.inviteCode());
        stockListingService.list(created.leagueId(), first, "종목하나", "소개");

        assertThatThrownBy(() -> useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId()))
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_STARTABLE);

        stockListingService.list(created.leagueId(), second, "종목둘", "소개");
        StartedLeague started = useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        assertThat(started.leagueId()).isEqualTo(created.leagueId());
    }

    @Test
    @DisplayName("리그가 시작되면 시작 일시부터 종료 시각까지 빈틈없이 이어지는 1~7일차 거래일이 저장된다")
    void createsTradingDays() {
        CreatedLeague created = leagueWithParticipants(3);

        useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        entityManager.flush();
        entityManager.clear();
        League league = entityManager.find(League.class, created.leagueId());
        List<TradingDay> tradingDays = entityManager.getEntityManager()
                .createQuery("select t from TradingDay t where t.leagueId = :leagueId"
                        + " order by t.dayNumber", TradingDay.class)
                .setParameter("leagueId", created.leagueId())
                .getResultList();
        assertThat(tradingDays).extracting(TradingDay::getDayNumber)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(tradingDays.getFirst().getStartsAt()).isEqualTo(league.getStartedAt());
        assertThat(tradingDays.getLast().getEndsAt()).isEqualTo(league.getEndsAt());
        for (int i = 1; i < tradingDays.size(); i++) {
            assertThat(tradingDays.get(i).getStartsAt())
                    .isEqualTo(tradingDays.get(i - 1).getEndsAt());
        }
    }

    @Test
    @DisplayName("리그를 시작하지 못하면 거래일도 저장되지 않는다")
    void noTradingDaysWhenNotStartable() {
        CreatedLeague created = leagueWithParticipants(2);

        assertThatThrownBy(() -> useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId()))
                .isInstanceOf(NaspiBizException.class);

        Long count = entityManager.getEntityManager()
                .createQuery("select count(t) from TradingDay t where t.leagueId = :leagueId",
                        Long.class)
                .setParameter("leagueId", created.leagueId())
                .getSingleResult();
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("리그가 시작되면 같은 트랜잭션에서 모든 참여자에게 초기 자산이 배분된다")
    void allocatesInitialAssets() {
        CreatedLeague created = leagueWithParticipants(3);

        useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        assertThat(countAccounts(created.leagueId())).isEqualTo(3);
    }

    @Test
    @DisplayName("리그가 시작되면 모든 종목의 현재가가 시초가 10,000원이 된다")
    void opensAllStocksAtOpeningPrice() {
        CreatedLeague created = leagueWithParticipants(3);
        assertThat(stockQueryService.findAllByLeagueId(created.leagueId()))
                .extracting(LeagueStock::currentPrice).containsOnlyNulls();

        useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        entityManager.flush();
        entityManager.clear();
        assertThat(stockQueryService.findAllByLeagueId(created.leagueId()))
                .hasSize(3)
                .extracting(LeagueStock::currentPrice).containsOnly(10_000L);
    }

    @Test
    @DisplayName("리그 시작 조건을 만족하지 않아 시작이 거절되면 종목 현재가도 정해지지 않는다")
    void doesNotOpenStocksWhenStartRejected() {
        CreatedLeague created = leagueWithParticipants(2);

        assertThatThrownBy(() -> useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId()))
                .isInstanceOf(NaspiBizException.class);

        assertThat(stockQueryService.findAllByLeagueId(created.leagueId()))
                .extracting(LeagueStock::currentPrice).containsOnlyNulls();
    }

    @Test
    @DisplayName("리그 시작 이후에는 기존 초대 코드로 추가 참여할 수 없고, 이미 시작된 리그임을 알린다")
    void cannotJoinAfterStart() {
        CreatedLeague created = leagueWithParticipants(3);
        useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        assertThatThrownBy(() -> leagueService.join(nextMemberId++, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("리그 시작 권한은 방장에게만 있다")
    void onlyHostCanStart() {
        CreatedLeague created = useCase.create(HOST_MEMBER_ID, "리그", "나스피", "소개");
        long participant = participant(created);
        participant(created);

        assertStartFails(participant, created, ErrorCode.LEAGUE_HOST_ONLY);
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 리그를 시작할 수 없다")
    void outsiderCannotStart() {
        CreatedLeague created = leagueWithParticipants(3);

        assertStartFails(nextMemberId++, created, ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("상장을 마친 참여자가 방장 포함 3명 미만이면 리그를 시작할 수 없다")
    void tooFewParticipants() {
        CreatedLeague created = leagueWithParticipants(2);

        assertStartFails(HOST_MEMBER_ID, created, ErrorCode.LEAGUE_NOT_STARTABLE);
    }

    @Test
    @DisplayName("대기자가 1명이라도 있으면 리그를 시작할 수 없다")
    void waitingPlayerExists() {
        CreatedLeague created = leagueWithParticipants(3);
        leagueService.join(nextMemberId++, created.inviteCode());

        assertStartFails(HOST_MEMBER_ID, created, ErrorCode.LEAGUE_NOT_STARTABLE);
    }

    @Test
    @DisplayName("이미 시작된 리그는 다시 시작할 수 없다")
    void alreadyStarted() {
        CreatedLeague created = leagueWithParticipants(3);
        useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        assertThatThrownBy(() -> useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_ALREADY_STARTED);
        assertThat(countAccounts(created.leagueId())).isEqualTo(3);  // 다시 배분되지 않는다
    }

    @Test
    @DisplayName("생성 후 7일이 지났어도 만료 처리 전인 대기실은 시작할 수 있다")
    void startOverdueWaitingRoomBeforeExpiration() {
        CreatedLeague created = leagueWithParticipants(3);
        clock.advance(League.WAITING_ROOM_LIFETIME);

        useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        assertThat(entityManager.find(League.class, created.leagueId()).getStatus())
                .isEqualTo(LeagueStatus.STARTED);
    }

    @Test
    @DisplayName("만료 처리된 대기실은 시작할 수 없다")
    void expiredWaitingRoom() {
        CreatedLeague created = leagueWithParticipants(3);
        clock.advance(League.WAITING_ROOM_LIFETIME);
        entityManager.find(League.class, created.leagueId()).expire(LocalDateTime.now(clock));
        entityManager.flush();

        assertStartFails(HOST_MEMBER_ID, created, ErrorCode.LEAGUE_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 리그는 시작할 수 없다")
    void unknownLeague() {
        assertThatThrownBy(() -> useCaseUnderTest.start(HOST_MEMBER_ID, 999_999L))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
    }

    private void assertStartFails(long memberId, CreatedLeague created, ErrorCode errorCode) {
        assertThatThrownBy(() -> useCaseUnderTest.start(memberId, created.leagueId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(errorCode);
        assertThat(countAccounts(created.leagueId())).isZero();
    }

    /** 리그에 배분된 계좌 수. asset의 엔티티를 참조하지 않도록 JPQL로 센다. */
    private long countAccounts(long leagueId) {
        return entityManager.getEntityManager()
                .createQuery("select count(a) from Account a where a.leagueId = :leagueId",
                        Long.class)
                .setParameter("leagueId", leagueId)
                .getSingleResult();
    }

    /** 방장을 포함해 상장을 마친 참여자가 count명인 리그를 만든다. */
    private CreatedLeague leagueWithParticipants(int count) {
        CreatedLeague created = useCase.create(HOST_MEMBER_ID, "리그", "나스피", "소개");
        for (int i = 1; i < count; i++) {
            participant(created);
        }
        return created;
    }

    /** 리그에 입장해 상장을 마친 참여자를 추가하고 회원 ID를 돌려준다. */
    private long participant(CreatedLeague created) {
        long memberId = nextMemberId++;
        JoinedLeague joined = leagueService.join(memberId, created.inviteCode());
        // 리그 시작 시 초기 자산을 배분하려면 참여자마다 실제 종목이 상장되어 있어야 한다.
        stockListingService.list(created.leagueId(), memberId, "종목" + joined.playerId(), "소개");
        return memberId;
    }
}
