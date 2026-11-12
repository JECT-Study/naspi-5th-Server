package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.market.application.StockListingService;
import kr.ject.naspi.market.application.StockQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({LeagueStartUseCase.class, LeagueService.class, LeagueCreationUseCase.class,
        InviteCodeIssuer.class, LeagueJoinLimit.class, StockListingService.class,
        StockQueryService.class,
        MutableClock.Config.class})
class LeagueStartUseCaseTest {

    private static final long HOST_MEMBER_ID = 1L;

    @Autowired
    private LeagueStartUseCase useCaseUnderTest;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueCreationUseCase useCase;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ApplicationEvents events;

    private long nextMemberId = 100L;

    @Test
    @DisplayName("방장은 참여자가 3명 이상이고 대기자가 없으면 리그를 시작할 수 있다")
    void start() {
        CreatedLeague created = leagueWithParticipants(3);

        StartedLeague started = useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        LocalDateTime now = LocalDateTime.now(clock);
        assertThat(started).isEqualTo(new StartedLeague(created.leagueId(), now));
        League league = entityManager.find(League.class, created.leagueId());
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.STARTED);
        assertThat(league.getStartedAt()).isEqualTo(now);
        assertThat(league.getInviteCode()).isNull();
    }

    @Test
    @DisplayName("리그가 시작되면 리그의 상장 종목을 담아 리그 시작 이벤트를 발행한다 (초기 자산 배분)")
    void publishLeagueStarted() {
        CreatedLeague created = leagueWithParticipants(3);

        useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        // 테스트에서는 방장만 실제 종목을 상장하고, 다른 참여자는 상장 완료 처리만 한다.
        assertThat(events.stream(LeagueStarted.class)).singleElement().satisfies(event -> {
            assertThat(event.leagueId()).isEqualTo(created.leagueId());
            assertThat(event.listedStocks()).hasSize(1);
        });
    }

    @Test
    @DisplayName("리그 시작 이후에는 기존 초대 코드로 추가 참여할 수 없다")
    void cannotJoinAfterStart() {
        CreatedLeague created = leagueWithParticipants(3);
        useCaseUnderTest.start(HOST_MEMBER_ID, created.leagueId());

        assertThatThrownBy(() -> leagueService.join(nextMemberId++, created.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
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
        events.clear();

        assertStartFails(HOST_MEMBER_ID, created, ErrorCode.LEAGUE_ALREADY_STARTED);
    }

    @Test
    @DisplayName("생성 후 7일이 지나 만료된 대기실은 시작할 수 없다")
    void expiredWaitingRoom() {
        CreatedLeague created = leagueWithParticipants(3);
        clock.advance(League.WAITING_ROOM_LIFETIME);

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
        assertThat(events.stream(LeagueStarted.class)).isEmpty();
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
        leagueService.completeListing(joined.playerId());
        return memberId;
    }
}
