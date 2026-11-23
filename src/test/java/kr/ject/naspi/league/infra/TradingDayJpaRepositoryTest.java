package kr.ject.naspi.league.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.league.domain.TradingDay;
import kr.ject.naspi.league.domain.TradingDayRepository;
import kr.ject.naspi.league.domain.TradingSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
class TradingDayJpaRepositoryTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 15, 10, 0);
    private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Autowired
    private TradingDayRepository tradingDayRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("거래일의 마감 일시를 저장하고 일차 순서로 조회한다 (마감 전에는 비어 있다)")
    void persistClosedAt() {
        tradingDayRepository.saveAll(TradingDay.of(1L, new TradingSchedule(STARTED_AT)));
        tradingDayRepository.saveAll(TradingDay.of(2L, new TradingSchedule(STARTED_AT)));
        entityManager.flush();
        entityManager.clear();

        List<TradingDay> days = tradingDayRepository.findAllByLeagueIdOrderByDayNumber(1L);
        assertThat(days).extracting(TradingDay::getDayNumber).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(days).allMatch(day -> day.getClosedAt() == null);

        days.get(0).close(DAY2);
        entityManager.flush();
        entityManager.clear();

        List<TradingDay> found = tradingDayRepository.findAllByLeagueIdOrderByDayNumber(1L);
        assertThat(found.get(0).getClosedAt()).isEqualTo(DAY2);
        assertThat(found.get(1).getClosedAt()).isNull();
    }
}
