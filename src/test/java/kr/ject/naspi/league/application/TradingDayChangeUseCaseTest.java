package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.league.domain.TradingDayRepository;
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
@Import(TradingDayChangeUseCase.class)
class TradingDayChangeUseCaseTest {

    /** 시작 후 1일차: 2026-11-15 10:00 시작, 2일차는 11-16 09:00. */
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Autowired
    private TradingDayChangeUseCase useCase;

    @Autowired
    private TradingDayRepository tradingDayRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("09:00 KST에 진행 중인 리그가 다음 거래일로 전환되어 직전 거래일이 마감된다")
    void changeToNextDay() {
        League league = startedLeague();

        assertThat(useCase.change(league.getId(), DAY2.minusSeconds(1))).isZero();
        assertThat(useCase.change(league.getId(), DAY2)).isEqualTo(1);

        assertThat(closedDayNumbers(league)).containsExactly(1);
    }

    @Test
    @DisplayName("같은 거래일 전환을 두 번 실행해도 한 번만 처리된다")
    void changeIsIdempotent() {
        League league = startedLeague();

        assertThat(useCase.change(league.getId(), DAY2)).isEqualTo(1);
        assertThat(useCase.change(league.getId(), DAY2.plusSeconds(5))).isZero();

        assertThat(closedDayNumbers(league)).containsExactly(1);
    }

    @Test
    @DisplayName("서버가 내려가 놓친 거래일 전환은 다음 실행에서 순서대로 보정된다")
    void catchUpMissedChanges() {
        League league = startedLeague();

        assertThat(useCase.change(league.getId(), DAY2.plusDays(2).plusSeconds(1))).isEqualTo(3);

        assertThat(closedDayNumbers(league)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("종료 시각 이후 기동해도 7일차 전까지 밀린 전환을 보정하고 7일차는 마감하지 않는다")
    void catchUpAfterEnd() {
        League league = startedLeague();
        useCase.change(league.getId(), DAY2.plusDays(2));

        assertThat(useCase.change(league.getId(), league.getEndsAt().plusDays(1))).isEqualTo(3);

        assertThat(closedDayNumbers(league)).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("대기실·만료 리그는 거래일 전환을 하지 않는다")
    void skipNotStarted() {
        League waiting = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("AAAAAAA"), STARTED_AT));
        League expired = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("BBBBBBB"), STARTED_AT));
        ReflectionTestUtils.setField(expired, "status", LeagueStatus.EXPIRED);
        entityManager.flush();

        assertThat(useCase.change(waiting.getId(), DAY2.plusDays(3))).isZero();
        assertThat(useCase.change(expired.getId(), DAY2.plusDays(3))).isZero();
        assertThat(useCase.change(999_999L, DAY2)).isZero();
    }

    private List<Integer> closedDayNumbers(League league) {
        entityManager.flush();
        entityManager.clear();
        return tradingDayRepository.findAllByLeagueIdOrderByDayNumber(league.getId()).stream()
                .filter(TradingDay::isClosed)
                .map(TradingDay::getDayNumber)
                .toList();
    }

    private League startedLeague() {
        League league = entityManager.persistAndFlush(League.create(
                LeagueName.of("리그"), InviteCode.of("CCCCCCC"), STARTED_AT.minusDays(1)));
        List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(league.getId(), memberId,
                            STARTED_AT.minusDays(1));
                    player.completeListing(STARTED_AT.minusDays(1));
                    return player;
                })
                .toList();
        league.start(players, STARTED_AT);
        tradingDayRepository.saveAll(TradingDay.of(league.getId(), league.tradingSchedule()));
        return entityManager.persistAndFlush(league);
    }
}
