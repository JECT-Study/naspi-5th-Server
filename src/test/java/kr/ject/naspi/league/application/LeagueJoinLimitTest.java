package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.market.application.StockListingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueCreationUseCase.class, LeagueService.class, LeagueJoinLimit.class,
        InviteCodeIssuer.class, StockListingService.class, MutableClock.Config.class})
class LeagueJoinLimitTest {

    private static final long MEMBER_ID = 1L;
    private static final long HOST_MEMBER_ID = 100L;

    @Autowired
    private LeagueCreationUseCase useCase;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MutableClock clock;

    private long otherHostId = HOST_MEMBER_ID;

    @Test
    @DisplayName("회원은 대기실과 진행 중인 리그를 합쳐 최대 5개까지 참여할 수 있다")
    void joinUpToLimit() {
        List<CreatedLeague> joined = joinLeagues(LeagueJoinLimit.MAX_JOINED_LEAGUES);

        assertThat(joined).hasSize(5);
    }

    @Test
    @DisplayName("이미 리그 5개에 참여 중이면 리그를 생성할 수 없다")
    void createOverLimit() {
        joinLeagues(LeagueJoinLimit.MAX_JOINED_LEAGUES);

        assertThatThrownBy(() -> useCase.create(MEMBER_ID, "리그", "나스피", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_JOIN_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("이미 리그 5개에 참여 중이면 초대 코드로 대기실에 입장할 수 없다")
    void joinOverLimit() {
        joinLeagues(LeagueJoinLimit.MAX_JOINED_LEAGUES);
        CreatedLeague another = otherLeague();

        assertThatThrownBy(() -> leagueService.join(MEMBER_ID, another.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_JOIN_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("진행 중인 리그는 참여 개수에 포함된다")
    void startedLeagueCounted() {
        List<CreatedLeague> joined = joinLeagues(LeagueJoinLimit.MAX_JOINED_LEAGUES);
        changeStatus(joined.get(0), LeagueStatus.STARTED);

        assertThatThrownBy(() -> useCase.create(MEMBER_ID, "리그", "나스피", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_JOIN_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("종료된 리그는 참여 개수에서 제외된다")
    void endedLeagueExcluded() {
        List<CreatedLeague> joined = joinLeagues(LeagueJoinLimit.MAX_JOINED_LEAGUES);
        changeStatus(joined.get(0), LeagueStatus.ENDED);
        CreatedLeague created = useCase.create(MEMBER_ID, "리그", "나스피", "소개");

        changeStatus(joined.get(1), LeagueStatus.ENDED);
        JoinedLeague entered = leagueService.join(MEMBER_ID, otherLeague().inviteCode());

        assertThat(created.leagueId()).isNotZero();
        assertThat(entered.leagueId()).isNotZero();
    }

    @Test
    @DisplayName("만료된 대기실은 참여 개수에서 제외된다")
    void expiredLeagueExcluded() {
        CreatedLeague expiring = useCase.create(MEMBER_ID, "만료될 리그", "나스피", "소개");
        clock.advance(League.WAITING_ROOM_LIFETIME);
        joinLeagues(LeagueJoinLimit.MAX_JOINED_LEAGUES - 1);
        entityManager.find(League.class, expiring.leagueId()).expire(LocalDateTime.now(clock));
        entityManager.flush();

        CreatedLeague created = useCase.create(MEMBER_ID, "리그", "나스피", "소개");

        assertThat(created.leagueId()).isNotZero();
    }

    @Test
    @DisplayName("생성 후 7일이 지난 대기실은 만료 처리 전이어도 참여 개수에서 제외된다")
    void overdueWaitingRoomExcluded() {
        useCase.create(MEMBER_ID, "만료될 리그", "나스피", "소개");
        clock.advance(League.WAITING_ROOM_LIFETIME);
        joinLeagues(LeagueJoinLimit.MAX_JOINED_LEAGUES - 1);

        CreatedLeague created = useCase.create(MEMBER_ID, "리그", "나스피", "소개");

        assertThat(created.leagueId()).isNotZero();
    }

    @Test
    @DisplayName("생성 후 7일이 지나지 않은 대기실은 참여 개수에 포함된다")
    void waitingRoomBeforeExpiryCounted() {
        useCase.create(MEMBER_ID, "리그", "나스피", "소개");
        clock.advance(League.WAITING_ROOM_LIFETIME.minusSeconds(1));
        joinLeagues(LeagueJoinLimit.MAX_JOINED_LEAGUES - 1);

        assertThatThrownBy(() -> useCase.create(MEMBER_ID, "리그", "나스피", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_JOIN_LIMIT_EXCEEDED);
    }

    /** 회원이 직접 만든 리그와 초대받은 리그를 섞어 count개에 참여시킨다. */
    private List<CreatedLeague> joinLeagues(int count) {
        List<CreatedLeague> leagues = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                leagues.add(useCase.create(MEMBER_ID, "리그" + i, "나스피", "소개"));
            } else {
                CreatedLeague league = otherLeague();
                leagueService.join(MEMBER_ID, league.inviteCode());
                leagues.add(league);
            }
        }
        return leagues;
    }

    private CreatedLeague otherLeague() {
        return useCase.create(otherHostId++, "다른 리그", "나스피", "소개");
    }

    private void changeStatus(CreatedLeague created, LeagueStatus status) {
        League league = entityManager.find(League.class, created.leagueId());
        // TODO: 리그 시작(LEA-09)·종료(LEA-12) 구현 후 도메인 메서드로 바꾼다
        ReflectionTestUtils.setField(league, "status", status);
        entityManager.flush();
    }
}
