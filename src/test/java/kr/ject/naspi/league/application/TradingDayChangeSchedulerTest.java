package kr.ject.naspi.league.application;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TradingDayChangeSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Test
    @DisplayName("한 리그의 전환이 실패해도 다른 리그의 전환은 계속 처리된다")
    void continueAfterLeagueFailure() {
        LeagueRepository leagueRepository = mock(LeagueRepository.class);
        TradingDayChangeUseCase useCase = mock(TradingDayChangeUseCase.class);
        when(leagueRepository.findIdsByStatus(LeagueStatus.STARTED)).thenReturn(List.of(1L, 2L));
        when(useCase.change(eq(1L), eq(NOW))).thenThrow(new IllegalStateException("실패"));
        when(useCase.change(eq(2L), eq(NOW))).thenReturn(1);

        Clock clock = Clock.fixed(NOW.atZone(ClockConfig.KST).toInstant(), ClockConfig.KST);

        new TradingDayChangeScheduler(leagueRepository, useCase, clock).changeTradingDays();

        verify(useCase).change(1L, NOW);
        verify(useCase).change(2L, NOW);
    }
}
