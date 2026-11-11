package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.market.application.StockListingService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueCreationUseCase.class, InviteCodeIssuer.class, LeagueJoinLimit.class,
        StockListingService.class, LeagueCreationUseCaseTest.FixedClock.class})
class LeagueCreationUseCaseTest {

    private static final long HOST_MEMBER_ID = 1L;
    private static final long OTHER_MEMBER_ID = 2L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 21, 0);

    @Autowired
    private LeagueCreationUseCase useCase;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            Instant instant = NOW.atZone(ClockConfig.KST).toInstant();
            return Clock.fixed(instant, ClockConfig.KST);
        }
    }

    @Test
    @DisplayName("리그를 생성하면 대기실 상태의 리그와 공통 초대 코드 1개가 생기고, 생성한 회원이 방장이 된다")
    void create() {
        CreatedLeague created = useCase.create(HOST_MEMBER_ID, "우리끼리 주식왕", "나스피", "소개");

        League league = entityManager.find(League.class, created.leagueId());
        assertThat(league.getName().getValue()).isEqualTo("우리끼리 주식왕");
        assertThat(league.getStatus()).isEqualTo(LeagueStatus.WAITING);
        assertThat(league.getInviteCode().getValue())
                .isEqualTo(created.inviteCode())
                .matches("^[A-Z0-9]{7}$");
        assertThat(league.getHostPlayerId()).isEqualTo(created.hostPlayerId());
        assertThat(league.getCreatedAt()).isEqualTo(NOW);

        Player host = entityManager.find(Player.class, created.hostPlayerId());
        assertThat(host.getLeagueId()).isEqualTo(created.leagueId());
        assertThat(host.getMemberId()).isEqualTo(HOST_MEMBER_ID);
        assertThat(host.getJoinedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("방장이 리그 이름을 지정하고 상장을 마치면 리그가 생성되고, 방장은 참여자가 된다")
    void listHostStock() {
        CreatedLeague created = useCase.create(HOST_MEMBER_ID, "우리끼리 주식왕", "나스피", "소개");

        assertThat(stockRepository.findAllByLeagueId(created.leagueId()))
                .singleElement()
                .extracting(Stock::getListerPlayerId)
                .isEqualTo(created.hostPlayerId());

        Player host = entityManager.find(Player.class, created.hostPlayerId());
        assertThat(host.isListed()).isTrue();
        assertThat(host.getListedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("리그 이름은 다른 리그와 중복될 수 있다")
    void duplicatedNameAllowed() {
        CreatedLeague first = useCase.create(HOST_MEMBER_ID, "같은 이름", "나스피", "소개");
        CreatedLeague second = useCase.create(OTHER_MEMBER_ID, "같은 이름", "나스피", "소개");

        assertThat(second.leagueId()).isNotEqualTo(first.leagueId());
    }

    @Test
    @DisplayName("리그 이름이 12자를 넘으면 리그를 생성할 수 없고, 방장의 종목도 상장되지 않는다")
    void createWithTooLongName() {
        assertThatThrownBy(() -> useCase.create(HOST_MEMBER_ID, "가".repeat(13), "나스피", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_LEAGUE_NAME);

        assertThat(count("League")).isZero();
        assertThat(count("Stock")).isZero();
    }

    @Test
    @DisplayName("방장의 종목이 상장 규칙에 맞지 않으면 리그를 생성할 수 없다")
    void failLeagueCreationWhenHostStockInvalid() {
        assertThatThrownBy(() -> useCase.create(HOST_MEMBER_ID, "리그", "특수문자!", "소개"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_NAME);
    }

    private long count(String entity) {
        return entityManager.getEntityManager()
                .createQuery("select count(e) from " + entity + " e", Long.class)
                .getSingleResult();
    }
}
