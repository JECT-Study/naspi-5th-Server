package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDateTime;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.StockListingService;
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
        MemberService.class, ClockConfig.class})
class LeagueQueryServiceTest {

    private long kakaoId = 1000L;

    @Autowired
    private LeagueQueryService queryService;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private LeagueCreationUseCase useCase;

    @Autowired
    private TestEntityManager entityManager;

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

    private CreatedLeague leagueWithParticipants(int count) {
        CreatedLeague league = useCase.create(member("방장"), "리그", "나스피", "소개");
        for (int i = 1; i < count; i++) {
            JoinedLeague joined = leagueService.join(member("참여자"), league.inviteCode());
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
