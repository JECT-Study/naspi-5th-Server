package kr.ject.naspi.league.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.TradingDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@DataJpaTest
@Import({LeagueTradingService.class, MutableClock.Config.class})
class LeagueTradingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Autowired
    private LeagueTradingService service;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("시작되어 진행 중인 리그는 거래 락을 걸 수 있다")
    void lockStartedLeague() {
        League league = startedLeague();
        clock.setTo(NOW);

        assertThat(service.lockForTrading(league.getId())).isEqualTo(NOW);
    }

    @Test
    @DisplayName("시작하지 않은 대기실에서는 거래할 수 없다")
    void waitingLeagueIsNotTradable() {
        clock.setTo(NOW);
        League league = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("AAAAAAA"), NOW));

        assertThatThrownBy(() -> service.lockForTrading(league.getId()))
                .isInstanceOfSatisfying(NaspiBizException.class, e ->
                        assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_NOT_TRADABLE));
    }

    @Test
    @DisplayName("종료 처리 전이라도 종료 시각이 지난 리그에서는 거래할 수 없다")
    void endsAtPassedLeagueIsNotTradable() {
        League league = startedLeague();
        clock.setTo(league.getEndsAt());

        assertThatThrownBy(() -> service.lockForTrading(league.getId()))
                .isInstanceOfSatisfying(NaspiBizException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LEAGUE_ALREADY_ENDED));
    }

    @Test
    @DisplayName("만료된 대기실은 없는 리그로 거절한다")
    void expiredWaitingRoomIsNotFound() {
        League league = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("AAAAAAA"), NOW));
        clock.setTo(NOW.plusDays(7));

        assertThatThrownBy(() -> service.lockForTrading(league.getId()))
                .isInstanceOfSatisfying(NaspiBizException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LEAGUE_NOT_FOUND));
    }

    @Test
    @DisplayName("없는 리그는 거래 락을 걸 수 없다")
    void leagueNotFound() {
        assertThatThrownBy(() -> service.lockForTrading(999_999L))
                .isInstanceOfSatisfying(NaspiBizException.class, e ->
                        assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.LEAGUE_NOT_FOUND));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("진행 중인 트랜잭션이 없으면 락이 바로 풀리므로 거래 락을 걸 수 없다")
    void requiresTransaction() {
        assertThatThrownBy(() -> service.lockForTrading(1L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("1일차 주문은 전환 확인 없이 접수된다")
    void firstDayNeedsNoChange() {
        League league = startedLeague();

        assertThat(service.requireTradingDayOpened(league.getId(), NOW)).isEqualTo(1);
    }

    @Test
    @DisplayName("09:00 이후 거래일 전환 처리 전에는 주문을 받지 않는다")
    void rejectBeforeChange() {
        League league = startedLeagueWithDays();

        assertThatThrownBy(() -> service.requireTradingDayOpened(league.getId(), DAY2))
                .isInstanceOfSatisfying(NaspiBizException.class, e ->
                        assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.TRADING_DAY_CHANGE_PENDING));
    }

    @Test
    @DisplayName("거래일 전환을 마친 뒤에는 새 거래일 주문으로 접수된다")
    void acceptAfterChange() {
        League league = startedLeagueWithDays();
        entityManager.getEntityManager().createQuery(
                "select t from TradingDay t where t.leagueId = :l and t.dayNumber = 1",
                TradingDay.class).setParameter("l", league.getId()).getSingleResult().close(DAY2);
        entityManager.flush();

        assertThat(service.requireTradingDayOpened(league.getId(), DAY2)).isEqualTo(2);
    }

    @Test
    @DisplayName("09:00 직전은 아직 이전 거래일이라 전환 확인 없이 접수된다")
    void beforeNineIsPreviousDay() {
        League league = startedLeagueWithDays();

        assertThat(service.requireTradingDayOpened(league.getId(), DAY2.minusNanos(1_000_000)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("00:00~09:00에 시작한 리그는 당일 09:00 이후에도 1일차이며 전환이 없다")
    void dawnStartFirstDay() {
        LocalDateTime dawn = LocalDateTime.of(2026, 11, 2, 3, 0);
        League league = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("DDDDDDD"), dawn.minusDays(1)));
        List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(league.getId(), memberId, dawn.minusDays(1));
                    player.completeListing(dawn.minusDays(1));
                    return player;
                })
                .toList();
        league.start(players, dawn);
        entityManager.persistAndFlush(league);

        assertThat(service.requireTradingDayOpened(league.getId(), dawn.withHour(9).withMinute(30)))
                .isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("진행 중인 트랜잭션이 없으면 거래일 확인을 할 수 없다")
    void requireOpenedNeedsTransaction() {
        assertThatThrownBy(() -> service.requireTradingDayOpened(1L, NOW))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private League startedLeagueWithDays() {
        League league = startedLeague();
        TradingDay.of(league.getId(), league.tradingSchedule()).forEach(entityManager::persist);
        entityManager.flush();
        return league;
    }

    private League startedLeague() {
        League league = entityManager.persistAndFlush(
                League.create(LeagueName.of("리그"), InviteCode.of("AAAAAAA"), NOW.minusDays(1)));
        List<Player> players = LongStream.rangeClosed(1, League.MIN_PARTICIPANTS_TO_START)
                .mapToObj(memberId -> {
                    Player player = Player.enter(league.getId(), memberId, NOW.minusDays(1));
                    player.completeListing(NOW.minusDays(1));
                    return player;
                })
                .toList();
        league.start(players, NOW);
        return entityManager.persistAndFlush(league);
    }
}
