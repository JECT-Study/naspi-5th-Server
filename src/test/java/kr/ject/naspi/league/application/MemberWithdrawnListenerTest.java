package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import kr.ject.naspi.asset.application.InitialAssetAllocationService;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.StockListingService;
import kr.ject.naspi.market.application.StockOpeningService;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.infra.LocalStockSnapshotCache;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.member.domain.Member;
import kr.ject.naspi.member.domain.MemberName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({MemberWithdrawnListener.class, MemberService.class, LeagueLeaveUseCase.class,
        LeagueStartUseCase.class,
        StockOpeningService.class, LeagueService.class, LeagueCreationUseCase.class,
        InviteCodeIssuer.class, LeagueJoinLimit.class, LeagueQueryService.class,
        StockListingService.class,
        StockQueryService.class, LocalStockSnapshotCache.class,
        InitialAssetAllocationService.class, MutableClock.Config.class})
class MemberWithdrawnListenerTest {

    private long kakaoId = 1000L;

    @Autowired
    private MemberService memberService;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueCreationUseCase leagueCreationUseCase;

    @Autowired
    private LeagueStartUseCase leagueStartUseCase;

    @Autowired
    private StockListingService stockListingService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("회원 탈퇴 시 대기실에서는 퇴장되고, 시작된 리그에서는 플레이어가 남아 탈퇴 일시가 기록된다")
    void withdrawHandlesWaitingRoomAndStartedLeague() {
        long member = member("탈퇴자");
        final LocalDateTime now = LocalDateTime.now(clock);
        CreatedLeague started = startedLeagueWith(member);
        final long startedPlayerId = playerIdOf(started, member);
        CreatedLeague waiting = leagueCreationUseCase.create(member("방장"), "대기", "대기종목", "소개");
        final JoinedLeague waitingPlayer = leagueService.join(member, waiting.inviteCode());
        entityManager.flush();

        memberService.withdraw(member);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Player.class, waitingPlayer.playerId())).isNull();
        Player startedPlayer = entityManager.find(Player.class, startedPlayerId);
        assertThat(startedPlayer).isNotNull();
        assertThat(startedPlayer.getMemberId()).isEqualTo(member);
        assertThat(startedPlayer.getWithdrawnAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("탈퇴 일시는 탈퇴한 회원의 플레이어에만 기록된다")
    void recordWithdrawalIgnoresOtherMembers() {
        long member = member("탈퇴자");
        final CreatedLeague started = startedLeagueWith(member);

        leagueService.recordWithdrawal(member);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Player.class, started.hostPlayerId()).getWithdrawnAt())
                .isNull();
        assertThat(entityManager.find(Player.class, playerIdOf(started, member)).isWithdrawn())
                .isTrue();
    }

    @Test
    @DisplayName("리그 종료 시각이 지난 뒤 탈퇴하면 종료 후 탈퇴로 판정된다")
    void recordWithdrawalAfterEndsAt() {
        long early = member("일찍");
        long late = member("늦게");
        CreatedLeague league = startedLeagueWith(early, late);
        League found = entityManager.find(League.class, league.leagueId());

        leagueService.recordWithdrawal(early);
        clock.setTo(found.getEndsAt());
        leagueService.recordWithdrawal(late);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Player.class, playerIdOf(league, early))
                .isWithdrawnBefore(found.getEndsAt())).isTrue();
        assertThat(entityManager.find(Player.class, playerIdOf(league, late))
                .isWithdrawnBefore(found.getEndsAt())).isFalse();
    }

    /** 방장 + 주어진 회원들이 상장한 참여자인 시작된 리그를 만든다. 3명 미만이면 참여자를 더 채운다. */
    private CreatedLeague startedLeagueWith(long... members) {
        long host = member("방장");
        CreatedLeague league = leagueCreationUseCase.create(host, "리그", "나스피", "소개");
        int count = 1;
        for (long memberId : members) {
            join(league, memberId);
            count++;
        }
        while (count < 3) {
            join(league, member("참여"));
            count++;
        }
        leagueStartUseCase.start(host, league.leagueId());
        return league;
    }

    private void join(CreatedLeague league, long memberId) {
        JoinedLeague joined = leagueService.join(memberId, league.inviteCode());
        stockListingService.list(league.leagueId(), memberId, "종목" + joined.playerId(), "소개");
    }

    private long playerIdOf(CreatedLeague league, long memberId) {
        return entityManager.getEntityManager()
                .createQuery("select p.id from Player p where p.leagueId = :l and p.memberId = :m",
                        Long.class)
                .setParameter("l", league.leagueId())
                .setParameter("m", memberId)
                .getSingleResult();
    }

    private long member(String name) {
        Member member = entityManager.persist(
                Member.join(kakaoId++, MemberName.of(name), LocalDateTime.now()));
        return member.getId();
    }
}
