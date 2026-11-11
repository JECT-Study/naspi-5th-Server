package kr.ject.naspi.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.CreatedLeague;
import kr.ject.naspi.league.application.JoinedLeague;
import kr.ject.naspi.league.application.LeagueCreationUseCase;
import kr.ject.naspi.league.application.LeagueService;
import kr.ject.naspi.market.application.LeagueDetail.DetailPlayer;
import kr.ject.naspi.market.application.LeagueDetail.PlayerStock;
import kr.ject.naspi.member.application.MemberService;
import kr.ject.naspi.member.domain.Member;
import kr.ject.naspi.member.domain.MemberName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueDetailService.class, StockListingService.class,
        MemberService.class, ClockConfig.class})
@ComponentScan(basePackageClasses = LeagueService.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = ".*\\.(LeagueCreationUseCase|LeagueService"
                        + "|LeagueQueryService|InviteCodeIssuer)"))
class LeagueDetailServiceTest {

    private long kakaoId = 1000L;

    @Autowired
    private LeagueDetailService detailService;

    @Autowired
    private LeagueCreationUseCase leagueCreationUseCase;

    @Autowired
    private LeagueService leagueService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("리그 상세에는 플레이어 목록과 각 참여자의 종목 정보가 함께 담기고, 대기자는 종목이 없다")
    void getDetail() {
        long host = member("방장");
        long guest = member("손님");
        CreatedLeague league = leagueCreationUseCase.create(host, "리그", "나스피",
                "성장 가능성 무한대");
        JoinedLeague joined = leagueService.join(guest, league.inviteCode());

        LeagueDetail detail = detailService.getDetail(guest, league.leagueId());

        assertThat(detail.startable()).isFalse();
        assertThat(detail.players()).hasSize(2);
        DetailPlayer hostPlayer = detail.players().get(0);
        assertThat(hostPlayer.playerId()).isEqualTo(league.hostPlayerId());
        assertThat(hostPlayer.host()).isTrue();
        assertThat(hostPlayer.listed()).isTrue();
        assertThat(hostPlayer.stock())
                .extracting(PlayerStock::name, PlayerStock::description)
                .containsExactly("나스피", "성장 가능성 무한대");

        DetailPlayer guestPlayer = detail.players().get(1);
        assertThat(guestPlayer.playerId()).isEqualTo(joined.playerId());
        assertThat(guestPlayer.listed()).isFalse();
        assertThat(guestPlayer.stock()).isNull();
    }

    @Test
    @DisplayName("리그에 입장하지 않은 회원은 리그 상세를 조회할 수 없다")
    void getDetailByOutsider() {
        CreatedLeague league = leagueCreationUseCase.create(member("방장"), "리그",
                "나스피", "소개");

        assertThatThrownBy(() -> detailService.getDetail(member("외부인"), league.leagueId()))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    private long member(String name) {
        Member member = entityManager.persist(
                Member.join(kakaoId++, MemberName.of(name), LocalDateTime.now()));
        return member.getId();
    }
}
