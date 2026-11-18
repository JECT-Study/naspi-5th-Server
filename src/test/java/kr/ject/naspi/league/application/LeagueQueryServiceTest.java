package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDateTime;
import kr.ject.naspi.asset.application.InitialAssetAllocationService;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.StockListingService;
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
@Import({LeagueQueryService.class, LeagueService.class, LeagueCreationUseCase.class,
        InviteCodeIssuer.class, LeagueJoinLimit.class, StockListingService.class,
        StockQueryService.class, LocalStockSnapshotCache.class,
        MemberService.class, LeagueStartUseCase.class, InitialAssetAllocationService.class,
        MutableClock.Config.class})
class LeagueQueryServiceTest {

    private long kakaoId = 1000L;

    @Autowired
    private StockListingService stockListingService;

    @Autowired
    private LeagueQueryService queryService;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueCreationUseCase useCase;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MutableClock clock;

    @Autowired
    private LeagueStartUseCase leagueStartUseCase;

    @Test
    @DisplayName("이미 시작된 리그는 시작 가능으로 표시하지 않는다")
    void startedLeagueNotStartable() {
        CreatedLeague league = leagueWithParticipants(3);
        long host = memberOf(league);
        leagueStartUseCase.start(host, league.leagueId());

        assertThat(queryService.getPlayers(host, league.leagueId()).startable()).isFalse();
    }

    @Test
    @DisplayName("초대 코드로 리그 이름, 상태, 입장한 사용자 수, 참여자(상장한 플레이어) 수를 조회한다")
    void getSummary() {
        long host = member("방장");
        CreatedLeague league = useCase.create(host, "우리 리그", "나스피", "소개");
        leagueService.join(member("대기자"), league.inviteCode());

        LeagueSummary summary = queryService.getSummary(league.inviteCode().toLowerCase());

        assertThat(summary).isEqualTo(
                new LeagueSummary(league.leagueId(), "우리 리그", "WAITING", 2, 1));
    }

    @Test
    @DisplayName("없는 초대 코드로는 리그 정보를 조회할 수 없다")
    void getSummaryWithUnknownCode() {
        assertThatThrownBy(() -> queryService.getSummary("ZZZZZZZ"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("초대가 마감된(시작·만료된) 리그는 초대 코드로 조회할 수 없다")
    void getSummaryWithClosedCode() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        entityManager.find(League.class, league.leagueId()).closeInvitation();
        entityManager.flush();

        assertThatThrownBy(() -> queryService.getSummary(league.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("생성 후 7일이 지난 대기실은 초대 코드로 조회할 수 없다")
    void getSummaryOfExpiredWaitingRoom() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        clock.advance(League.WAITING_ROOM_LIFETIME);

        assertThatThrownBy(() -> queryService.getSummary(league.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("만료된 대기실에는 참여했던 회원도 접근할 수 없다")
    void getPlayersOfExpiredWaitingRoom() {
        long host = member("방장");
        CreatedLeague league = useCase.create(host, "리그", "나스피", "소개");
        clock.advance(League.WAITING_ROOM_LIFETIME);

        assertThatThrownBy(() -> queryService.getPlayers(host, league.leagueId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LEAGUE_NOT_FOUND);
    }

    @Test
    @DisplayName("대기실에 진입한 사용자는 상장 완료 여부에 따라 대기자 또는 참여자로 구분되고, 방장도 상장하면 참여자다")
    void getPlayers() {
        long host = member("방장");
        long guest = member("손님");
        CreatedLeague league = useCase.create(host, "리그", "나스피", "소개");
        JoinedLeague joined = leagueService.join(guest, league.inviteCode());

        LeaguePlayers players = queryService.getPlayers(guest, league.leagueId());

        assertThat(players.players())
                .extracting(LeaguePlayer::playerId, LeaguePlayer::name, LeaguePlayer::host,
                        LeaguePlayer::listed)
                .containsExactly(
                        tuple(league.hostPlayerId(), "방장", true, true),
                        tuple(joined.playerId(), "손님", false, false));
        assertThat(players.players().get(1).listedAt()).isNull();
    }

    @Test
    @DisplayName("참여자 3명 이상이고 대기자가 없으면 리그를 시작할 수 있다")
    void startable() {
        CreatedLeague league = leagueWithParticipants(3);

        assertThat(queryService.getPlayers(memberOf(league), league.leagueId()).startable())
                .isTrue();
    }

    @Test
    @DisplayName("참여자가 3명 미만이면 리그를 시작할 수 없다")
    void notStartableWithTooFewParticipants() {
        CreatedLeague league = leagueWithParticipants(2);

        assertThat(queryService.getPlayers(memberOf(league), league.leagueId()).startable())
                .isFalse();
    }

    @Test
    @DisplayName("대기자가 1명이라도 있으면 리그를 시작할 수 없다")
    void notStartableWithWaitingPlayer() {
        CreatedLeague league = leagueWithParticipants(3);
        leagueService.join(member("대기자"), league.inviteCode());

        assertThat(queryService.getPlayers(memberOf(league), league.leagueId()).startable())
                .isFalse();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 플레이어 목록을 조회할 수 없다")
    void getPlayersByOutsider() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");

        assertThatThrownBy(() -> queryService.getPlayers(member("외부인"), league.leagueId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("대기실에 있는 동안 탈퇴한 회원은 이름 없이 목록에 남는다")
    void withdrawnMemberHasNoName() {
        long host = member("방장");
        long guest = member("손님");
        CreatedLeague league = useCase.create(host, "리그", "나스피", "소개");
        JoinedLeague joined = leagueService.join(guest, league.inviteCode());
        entityManager.remove(entityManager.find(Member.class, guest));
        entityManager.flush();

        LeaguePlayers players = queryService.getPlayers(host, league.leagueId());

        assertThat(players.players())
                .filteredOn(player -> player.playerId() == joined.playerId())
                .singleElement()
                .extracting(LeaguePlayer::name).isNull();
    }

    @Test
    @DisplayName("리그에 입장한 회원은 본인의 플레이어 ID를 찾을 수 있고, 입장하지 않은 회원은 찾을 수 없다")
    void findPlayerId() {
        long host = member("방장");
        CreatedLeague league = useCase.create(host, "리그", "나스피", "소개");
        long guest = member("손님");
        JoinedLeague joined = leagueService.join(guest, league.inviteCode());
        long outsider = member("외부인");

        assertThat(queryService.findPlayerId(host, league.leagueId()))
                .contains(league.hostPlayerId());
        assertThat(queryService.findPlayerId(guest, league.leagueId()))
                .contains(joined.playerId());
        assertThat(queryService.findPlayerId(outsider, league.leagueId())).isEmpty();
    }

    @Test
    @DisplayName("회원이 참여 중인 진행 중 리그의 플레이어를 모두 찾고, 대기실과 다른 회원은 제외한다")
    void findStartedPlayersOfMember() {
        CreatedLeague first = leagueWithParticipants(3);
        CreatedLeague second = leagueWithParticipants(3);
        long host = memberOf(first);
        final long guest = memberOf(second);
        // host를 두 번째 리그의 참여자로 입장시킨다.
        JoinedLeague joined = leagueService.join(host, second.inviteCode());
        stockListingService.list(second.leagueId(), joined.playerId(), "호스트종목", "소개");
        leagueService.completeListing(joined.playerId());
        leagueStartUseCase.start(host, first.leagueId());
        leagueStartUseCase.start(guest, second.leagueId());
        CreatedLeague waiting = useCase.create(host, "대기", "대기종목", "소개");

        assertThat(queryService.findStartedPlayersOfMember(host))
                .containsExactlyInAnyOrder(
                        new MemberPlayer(first.leagueId(), first.hostPlayerId()),
                        new MemberPlayer(second.leagueId(), joined.playerId()));
        assertThat(queryService.findStartedPlayersOfMember(host))
                .noneMatch(player -> player.leagueId() == waiting.leagueId());
        assertThat(queryService.findStartedPlayersOfMember(member("외부인"))).isEmpty();
    }

    @Test
    @DisplayName("진행 중인 리그는 매일 09:00 KST에 거래일이 1일씩 늘어난다")
    void tradingDayAdvancesAtNine() {
        clock.setTo(LocalDateTime.of(2026, 11, 16, 15, 0));
        CreatedLeague league = leagueWithParticipants(3);
        long host = memberOf(league);
        leagueStartUseCase.start(host, league.leagueId());

        LeagueTradingDay first = queryService.getPlayers(host, league.leagueId()).tradingDay();
        assertThat(first).isEqualTo(new LeagueTradingDay(1, 7,
                LocalDateTime.of(2026, 11, 17, 9, 0),
                LocalDateTime.of(2026, 11, 16, 15, 0), LocalDateTime.of(2026, 11, 23, 9, 0)));

        clock.setTo(LocalDateTime.of(2026, 11, 17, 8, 59, 59));
        assertThat(queryService.getPlayers(host, league.leagueId()).tradingDay().dayNumber())
                .isEqualTo(1);

        clock.setTo(LocalDateTime.of(2026, 11, 17, 9, 0));
        LeagueTradingDay second = queryService.getPlayers(host, league.leagueId()).tradingDay();
        assertThat(second.dayNumber()).isEqualTo(2);
        assertThat(second.dayEndsAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));

        clock.setTo(LocalDateTime.of(2026, 11, 22, 9, 0));
        LeagueTradingDay last = queryService.getPlayers(host, league.leagueId()).tradingDay();
        assertThat(last.dayNumber()).isEqualTo(7);
        assertThat(last.dayEndsAt()).isEqualTo(last.endsAt());
    }

    @Test
    @DisplayName("00:00~09:00 KST에 시작한 리그는 당일 09:00까지도 1일차로 표시한다")
    void tradingDayDawnStart() {
        clock.setTo(LocalDateTime.of(2026, 11, 16, 3, 0));
        CreatedLeague league = leagueWithParticipants(3);
        long host = memberOf(league);
        leagueStartUseCase.start(host, league.leagueId());

        clock.setTo(LocalDateTime.of(2026, 11, 16, 9, 0));
        LeagueTradingDay tradingDay = queryService.getPlayers(host, league.leagueId()).tradingDay();

        assertThat(tradingDay.dayNumber()).isEqualTo(1);
        assertThat(tradingDay.dayEndsAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 9, 0));
        assertThat(tradingDay.endsAt()).isEqualTo(LocalDateTime.of(2026, 11, 23, 9, 0));
    }

    @Test
    @DisplayName("아직 시작하지 않은 리그(대기실)는 거래일이 없다")
    void noTradingDayBeforeStart() {
        CreatedLeague league = leagueWithParticipants(3);

        assertThat(queryService.getPlayers(memberOf(league), league.leagueId()).tradingDay())
                .isNull();
    }

    private CreatedLeague leagueWithParticipants(int count) {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        for (int i = 1; i < count; i++) {
            JoinedLeague joined = leagueService.join(member("참여자"), league.inviteCode());
            // 리그 시작 시 초기 자산을 배분하려면 참여자마다 실제 종목이 상장되어 있어야 한다.
            stockListingService.list(league.leagueId(), joined.playerId(),
                    "종목" + joined.playerId(), "소개");
            leagueService.completeListing(joined.playerId());
        }
        return league;
    }

    private long memberOf(CreatedLeague league) {
        return entityManager.find(Player.class, league.hostPlayerId())
                .getMemberId();
    }

    private long member(String name) {
        Member member = entityManager.persist(
                Member.join(kakaoId++, MemberName.of(name), LocalDateTime.now()));
        return member.getId();
    }
}
