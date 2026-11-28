package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.asset.application.InitialAssetAllocationService;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueQueryService.class, LeagueService.class, LeagueCreationUseCase.class,
        InviteCodeIssuer.class, LeagueJoinLimit.class, StockListingService.class,
        StockQueryService.class, LocalStockSnapshotCache.class, MemberService.class,
        LeagueStartUseCase.class,
        StockOpeningService.class, InitialAssetAllocationService.class, MutableClock.Config.class})
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
    @DisplayName("초대 코드가 폐기된(삭제·만료·종료된) 리그는 없는 코드로 안내한다")
    void getSummaryWithClosedCode() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        entityManager.find(League.class, league.leagueId()).closeInvitation();
        entityManager.flush();

        assertThatThrownBy(() -> queryService.getSummary(league.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("생성 후 7일이 지났어도 만료 처리 전인 대기실은 초대 코드로 조회할 수 있다")
    void getSummaryOfOverdueWaitingRoom() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        clock.advance(League.WAITING_ROOM_LIFETIME);

        assertThat(queryService.getSummary(league.inviteCode()).leagueId())
                .isEqualTo(league.leagueId());
    }

    @Test
    @DisplayName("만료 처리된 대기실은 초대 코드로 조회할 수 없다")
    void getSummaryOfExpiredWaitingRoom() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        clock.advance(League.WAITING_ROOM_LIFETIME);
        expire(league.leagueId());

        assertThatThrownBy(() -> queryService.getSummary(league.inviteCode()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("만료 처리된 대기실에는 참여했던 회원도 접근할 수 없다")
    void getPlayersOfExpiredWaitingRoom() {
        long host = member("방장");
        CreatedLeague league = useCase.create(host, "리그", "나스피", "소개");
        clock.advance(League.WAITING_ROOM_LIFETIME);
        expire(league.leagueId());

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
    @DisplayName("대기실 조회 시 리그 이름, 초대 코드, 대기실 만료 일시(생성 후 7일)와 플레이어별 본인 여부를 함께 조회한다")
    void getPlayersOfWaitingRoom() {
        long host = member("방장");
        long guest = member("손님");
        LocalDateTime createdAt = LocalDateTime.now(clock);
        CreatedLeague league = useCase.create(host, "우리 리그", "나스피", "소개");
        JoinedLeague joined = leagueService.join(guest, league.inviteCode());

        LeaguePlayers players = queryService.getPlayers(guest, league.leagueId());

        assertThat(players.name()).isEqualTo("우리 리그");
        assertThat(players.inviteCode()).isEqualTo(league.inviteCode());
        assertThat(players.waitingRoomExpiresAt())
                .isEqualTo(createdAt.plus(League.WAITING_ROOM_LIFETIME));
        assertThat(players.players())
                .extracting(LeaguePlayer::playerId, LeaguePlayer::me)
                .containsExactly(
                        tuple(league.hostPlayerId(), false),
                        tuple(joined.playerId(), true));
    }

    @Test
    @DisplayName("시작된 리그는 초대 코드와 대기실 만료 일시 없이 조회한다")
    void getPlayersOfStartedLeagueHasNoInvitation() {
        CreatedLeague league = leagueWithParticipants(3);
        long host = memberOf(league);
        leagueStartUseCase.start(host, league.leagueId());

        LeaguePlayers players = queryService.getPlayers(host, league.leagueId());

        assertThat(players.inviteCode()).isNull();
        assertThat(players.waitingRoomExpiresAt()).isNull();
        assertThat(players.players()).filteredOn(LeaguePlayer::me)
                .singleElement()
                .extracting(LeaguePlayer::playerId).isEqualTo(league.hostPlayerId());
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
    @DisplayName("탈퇴 기록 없이 회원 정보가 없는 플레이어는 이름 없이 남는다")
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
    @DisplayName("진행 중인 리그에서 탈퇴한 참여자의 표시 이름은 '탈퇴한 사용자'다")
    void withdrawnParticipantShownAsWithdrawnUser() {
        CreatedLeague league = leagueWithParticipants(3);
        long host = memberOf(league);
        long guestMember = member("손님");
        JoinedLeague guest = leagueService.join(guestMember, league.inviteCode());
        stockListingService.list(league.leagueId(), guestMember, "손님종목", "소개");
        leagueStartUseCase.start(host, league.leagueId());
        long guestMemberId = entityManager.find(Player.class, guest.playerId()).getMemberId();
        leagueService.recordWithdrawal(guestMemberId);
        entityManager.remove(entityManager.find(Member.class, guestMemberId));
        entityManager.flush();

        LeaguePlayers players = queryService.getPlayers(host, league.leagueId());

        assertThat(players.players())
                .filteredOn(player -> player.playerId() == guest.playerId())
                .singleElement()
                .extracting(LeaguePlayer::name).isEqualTo("탈퇴한 사용자");
        assertThat(players.players())
                .filteredOn(player -> player.playerId() == league.hostPlayerId())
                .singleElement()
                .extracting(LeaguePlayer::name).isEqualTo("방장");
    }

    @Test
    @DisplayName("요청한 플레이어의 표시 이름만 조회하고, 탈퇴한 참여자는 '탈퇴한 사용자', 다른 리그 플레이어는 빠진다")
    void getPlayerNames() {
        CreatedLeague league = leagueWithParticipants(3);
        long host = memberOf(league);
        long guestMember = member("손님");
        final JoinedLeague guest = leagueService.join(guestMember, league.inviteCode());
        stockListingService.list(league.leagueId(), guestMember, "손님종목", "소개");
        leagueStartUseCase.start(host, league.leagueId());
        leagueService.recordWithdrawal(guestMember);
        entityManager.remove(entityManager.find(Member.class, guestMember));
        entityManager.flush();
        CreatedLeague otherLeague = useCase.create(member("남"), "다른 리그", "남종목", "소개");

        assertThat(queryService.getPlayerNames(league.leagueId(), List.of(league.hostPlayerId(),
                guest.playerId(), otherLeague.hostPlayerId())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        league.hostPlayerId(), "방장", guest.playerId(), "탈퇴한 사용자"));
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
    @DisplayName("주문 조회는 진행 중인 리그에서만 가능하며 대기실·종료 리그를 사유별 코드로 거절한다")
    void requireTradable() {
        clock.setTo(LocalDateTime.of(2026, 11, 16, 15, 0));
        CreatedLeague league = leagueWithParticipants(3);
        assertThatThrownBy(() -> queryService.requireTradable(league.leagueId()))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE));

        leagueStartUseCase.start(memberOf(league), league.leagueId());
        clock.setTo(LocalDateTime.of(2026, 11, 23, 8, 59, 59));
        queryService.requireTradable(league.leagueId());

        clock.setTo(LocalDateTime.of(2026, 11, 23, 9, 0));
        assertThatThrownBy(() -> queryService.requireTradable(league.leagueId()))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
        assertThatThrownBy(() -> queryService.requireTradable(Long.MAX_VALUE))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LEAGUE_NOT_FOUND));
    }

    @Test
    @DisplayName("진행 중인 리그만 현재 거래일을 알려주고, 대기실은 거래일이 없다")
    void findCurrentTradingDay() {
        clock.setTo(LocalDateTime.of(2026, 11, 16, 15, 0));
        CreatedLeague league = leagueWithParticipants(3);

        assertThat(queryService.findCurrentTradingDay(league.leagueId())).isEmpty();

        leagueStartUseCase.start(memberOf(league), league.leagueId());
        clock.setTo(LocalDateTime.of(2026, 11, 17, 9, 0));

        assertThat(queryService.findCurrentTradingDay(league.leagueId()))
                .get().extracting(LeagueTradingDay::dayNumber).isEqualTo(2);
        assertThatThrownBy(() -> queryService.findCurrentTradingDay(Long.MAX_VALUE))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LEAGUE_NOT_FOUND));
    }

    @Test
    @DisplayName("종료 시각부터 결과 페이지 외 리그 화면에 접근할 수 없다")
    void requireAccessiblePlayerAfterEndsAt() {
        clock.setTo(LocalDateTime.of(2026, 11, 16, 15, 0));
        CreatedLeague league = leagueWithParticipants(3);
        long host = memberOf(league);
        final long outsider = member("외부인");
        // 대기실은 접근할 수 있다
        assertThat(queryService.requireAccessiblePlayer(host, league.leagueId()))
                .isEqualTo(league.hostPlayerId());
        assertThat(queryService.findAccessiblePlayerId(host, league.leagueId()))
                .contains(league.hostPlayerId());

        leagueStartUseCase.start(host, league.leagueId());
        clock.setTo(LocalDateTime.of(2026, 11, 23, 8, 59, 59));
        assertThat(queryService.requireAccessiblePlayer(host, league.leagueId()))
                .isEqualTo(league.hostPlayerId());

        // 종료 처리 전이라도 종료 시각부터 막는다
        clock.setTo(LocalDateTime.of(2026, 11, 23, 9, 0));
        assertThatThrownBy(() -> queryService.requireAccessiblePlayer(host, league.leagueId()))
                .isInstanceOfSatisfying(NaspiBizException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
        assertThat(queryService.findAccessiblePlayerId(host, league.leagueId())).isEmpty();

        // 입장하지 않은 회원에게는 리그 상태를 알리지 않는다
        assertThatThrownBy(() -> queryService.requireAccessiblePlayer(outsider, league.leagueId()))
                .isInstanceOfSatisfying(NaspiBizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        // 종료 상태로 바뀐 뒤에도 막는다
        League ended = entityManager.find(League.class, league.leagueId());
        ended.end(LocalDateTime.now(clock));
        entityManager.flush();
        assertThatThrownBy(() -> queryService.requireAccessiblePlayer(host, league.leagueId()))
                .isInstanceOfSatisfying(NaspiBizException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
    }

    @Test
    @DisplayName("종료 시각부터 공시·댓글·반응을 작성할 수 없다 (현재 거래일이 없다)")
    void currentTradingDayEmptyAfterEndsAt() {
        clock.setTo(LocalDateTime.of(2026, 11, 16, 15, 0));
        CreatedLeague league = leagueWithParticipants(3);
        leagueStartUseCase.start(memberOf(league), league.leagueId());

        clock.setTo(LocalDateTime.of(2026, 11, 23, 8, 59, 59));
        assertThat(queryService.findCurrentTradingDay(league.leagueId()))
                .get().extracting(LeagueTradingDay::dayNumber).isEqualTo(7);

        clock.setTo(LocalDateTime.of(2026, 11, 23, 9, 0));
        assertThat(queryService.findCurrentTradingDay(league.leagueId())).isEmpty();
    }

    @Test
    @DisplayName("회원이 참여 중인 진행 중 리그의 플레이어를 모두 찾고, 대기실과 다른 회원은 제외한다")
    void findStartedPlayersOfMember() {
        CreatedLeague first = leagueWithParticipants(3);
        CreatedLeague second = leagueWithParticipants(3);
        long host = memberOf(first);
        final long guest = memberOf(second);
        // host를 두 번째 리그의 참여자로 입장시킨다.
        final JoinedLeague joined = leagueService.join(host, second.inviteCode());
        stockListingService.list(second.leagueId(), host, "호스트종목", "소개");
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

    @Test
    @DisplayName("회원이 입장한 대기실, 진행 중, 종료 리그를 저장된 상태 그대로 모두 조회한다")
    void getMyLeaguesIncludesWaitingStartedEnded() {
        clock.setTo(LocalDateTime.of(2026, 11, 16, 15, 0));
        long me = member("나");
        CreatedLeague ended = useCase.create(me, "종료", "종료종목", "소개");
        ReflectionTestUtils.setField(entityManager.find(League.class, ended.leagueId()),
                "status", LeagueStatus.ENDED);
        CreatedLeague started = startedLeagueOf(me, "진행");
        CreatedLeague waiting = useCase.create(me, "대기", "대기종목", "소개");
        entityManager.flush();

        assertThat(queryService.getMyLeagues(me))
                .extracting(MyLeague::leagueId, MyLeague::status)
                .containsExactlyInAnyOrder(
                        tuple(waiting.leagueId(), LeagueStatus.WAITING),
                        tuple(started.leagueId(), LeagueStatus.STARTED),
                        tuple(ended.leagueId(), LeagueStatus.ENDED));
    }

    @Test
    @DisplayName("상장 전인 대기자도 입장한 대기실이 목록에 보이고 상장 여부가 false다")
    void getMyLeaguesIncludesWaitingPlayer() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        long guest = member("대기자");
        leagueService.join(guest, league.inviteCode());

        assertThat(queryService.getMyLeagues(guest))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.leagueId()).isEqualTo(league.leagueId());
                    assertThat(found.listed()).isFalse();
                    assertThat(found.host()).isFalse();
                });
    }

    @Test
    @DisplayName("생성 후 7일이 지났어도 만료 처리 전인 대기실은 저장된 상태(대기실)로 목록에 나온다")
    void getMyLeaguesKeepsStoredStatusOfOldWaitingRoom() {
        long me = member("나");
        useCase.create(me, "리그", "나스피", "소개");

        clock.advance(League.WAITING_ROOM_LIFETIME);

        assertThat(queryService.getMyLeagues(me))
                .extracting(MyLeague::status)
                .containsExactly(LeagueStatus.WAITING);
    }

    @Test
    @DisplayName("삭제된 대기실은 남아 있던 대기자의 목록에서도 제외한다")
    void getMyLeaguesExcludesDeletedWaitingRoom() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        long guest = member("대기자");
        leagueService.join(guest, league.inviteCode());
        ReflectionTestUtils.setField(entityManager.find(League.class, league.leagueId()),
                "status", LeagueStatus.DELETED);
        entityManager.flush();

        assertThat(queryService.getMyLeagues(guest)).isEmpty();
    }

    @Test
    @DisplayName("나가거나 추방되어 플레이어 행이 없는 대기실은 목록에서 제외한다")
    void getMyLeaguesExcludesLeftLeague() {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        long guest = member("손님");
        JoinedLeague joined = leagueService.join(guest, league.inviteCode());
        entityManager.remove(entityManager.find(Player.class, joined.playerId()));
        entityManager.flush();

        assertThat(queryService.getMyLeagues(guest)).isEmpty();
    }

    @Test
    @DisplayName("입장하지 않은 리그는 목록에 보이지 않는다")
    void getMyLeaguesExcludesOthersLeague() {
        useCase.create(member("방장"), "리그", "나스피", "소개");

        assertThat(queryService.getMyLeagues(member("외부인"))).isEmpty();
    }

    @Test
    @DisplayName("입장 인원은 참여자와 대기자를 합친 수, 참여자 수는 상장을 마친 수다")
    void getMyLeaguesCounts() {
        CreatedLeague league = leagueWithParticipants(2);
        long guest = member("대기자");
        leagueService.join(guest, league.inviteCode());

        assertThat(queryService.getMyLeagues(guest))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.playerCount()).isEqualTo(3);
                    assertThat(found.participantCount()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("방장인 리그만 방장으로 표시한다")
    void getMyLeaguesHostFlag() {
        long me = member("나");
        CreatedLeague mine = useCase.create(me, "내 리그", "내종목", "소개");
        CreatedLeague others = useCase.create(member("방장"), "남의 리그", "남종목", "소개");
        leagueService.join(me, others.inviteCode());

        assertThat(queryService.getMyLeagues(me))
                .extracting(MyLeague::leagueId, MyLeague::host)
                .containsExactlyInAnyOrder(
                        tuple(mine.leagueId(), true), tuple(others.leagueId(), false));
    }

    @Test
    @DisplayName("진행 중인 리그만 현재 거래일차를 함께 조회하고, 대기실은 만료 일시를 함께 조회한다")
    void getMyLeaguesTradingDay() {
        clock.setTo(LocalDateTime.of(2026, 11, 16, 15, 0));
        long me = member("나");
        CreatedLeague started = startedLeagueOf(me, "진행");
        CreatedLeague waiting = useCase.create(me, "대기", "대기종목", "소개");

        List<MyLeague> first = queryService.getMyLeagues(me);
        assertThat(first).filteredOn(found -> found.leagueId() == started.leagueId())
                .singleElement().satisfies(found -> {
                    assertThat(found.tradingDay()).isEqualTo(new LeagueTradingDay(1, 7,
                            LocalDateTime.of(2026, 11, 17, 9, 0),
                            LocalDateTime.of(2026, 11, 16, 15, 0),
                            LocalDateTime.of(2026, 11, 23, 9, 0)));
                    assertThat(found.waitingRoomExpiresAt()).isNull();
                });
        assertThat(first).filteredOn(found -> found.leagueId() == waiting.leagueId())
                .singleElement().satisfies(found -> {
                    assertThat(found.tradingDay()).isNull();
                    assertThat(found.waitingRoomExpiresAt())
                            .isEqualTo(LocalDateTime.of(2026, 11, 23, 15, 0));
                });

        clock.setTo(LocalDateTime.of(2026, 11, 17, 9, 0));
        assertThat(queryService.getMyLeagues(me))
                .filteredOn(found -> found.leagueId() == started.leagueId())
                .singleElement().extracting(found -> found.tradingDay().dayNumber())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("리그 목록은 상태와 관계없이 내 입장 일시 최신순, 같으면 리그 ID 내림차순이다")
    void getMyLeaguesOrder() {
        long me = member("나");
        clock.setTo(LocalDateTime.of(2026, 11, 16, 10, 0));
        final CreatedLeague first = useCase.create(me, "첫째", "첫째종목", "소개");
        final CreatedLeague second = useCase.create(me, "둘째", "둘째종목", "소개");
        clock.setTo(LocalDateTime.of(2026, 11, 16, 11, 0));
        CreatedLeague third = useCase.create(me, "셋째", "셋째종목", "소개");
        ReflectionTestUtils.setField(entityManager.find(League.class, third.leagueId()),
                "status", LeagueStatus.ENDED);
        entityManager.flush();

        assertThat(queryService.getMyLeagues(me)).extracting(MyLeague::leagueId)
                .containsExactly(third.leagueId(), second.leagueId(), first.leagueId());
    }

    @Test
    @DisplayName("입장한 리그가 없으면 빈 목록이다")
    void getMyLeaguesEmpty() {
        assertThat(queryService.getMyLeagues(member("나"))).isEmpty();
    }

    private CreatedLeague startedLeagueOf(long hostMemberId, String name) {
        CreatedLeague league = useCase.create(hostMemberId, name, name + "종목", "소개");
        for (int i = 0; i < 2; i++) {
            long memberId = member("참여자");
            JoinedLeague joined = leagueService.join(memberId, league.inviteCode());
            stockListingService.list(league.leagueId(), memberId,
                    "종목" + joined.playerId(), "소개");
        }
        leagueStartUseCase.start(hostMemberId, league.leagueId());
        return league;
    }

    private CreatedLeague leagueWithParticipants(int count) {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        for (int i = 1; i < count; i++) {
            long memberId = member("참여자");
            JoinedLeague joined = leagueService.join(memberId, league.inviteCode());
            // 리그 시작 시 초기 자산을 배분하려면 참여자마다 실제 종목이 상장되어 있어야 한다.
            stockListingService.list(league.leagueId(), memberId,
                    "종목" + joined.playerId(), "소개");
        }
        return league;
    }

    @Test
    @DisplayName("탈퇴한 회원이 다시 가입하면 탈퇴 전에 참여한 리그는 목록에 나오지 않는다")
    void getMyLeaguesExcludesLeaguesBeforeWithdrawal() {
        CreatedLeague league = leagueWithParticipants(3);
        long guestMember = member("손님");
        leagueService.join(guestMember, league.inviteCode());
        stockListingService.list(league.leagueId(), guestMember, "손님종목", "소개");
        leagueStartUseCase.start(memberOf(league), league.leagueId());
        leagueService.recordWithdrawal(guestMember);
        entityManager.remove(entityManager.find(Member.class, guestMember));
        entityManager.flush();

        long rejoined = member("손님");

        assertThat(queryService.getMyLeagues(rejoined)).isEmpty();
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

    private void expire(long leagueId) {
        entityManager.find(League.class, leagueId).expire(LocalDateTime.now(clock));
        entityManager.flush();
    }
}
