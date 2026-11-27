package kr.ject.naspi.league.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeagueEndSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 9, 0);

    private final LeagueRepository leagueRepository = mock(LeagueRepository.class);
    private final LeagueEndUseCase useCase = mock(LeagueEndUseCase.class);
    private final Clock clock = Clock.fixed(NOW.atZone(ClockConfig.KST).toInstant(),
            ClockConfig.KST);

    @Test
    @DisplayName("한 리그의 종료가 실패해도 다른 리그의 종료는 계속 처리된다")
    void continueAfterLeagueFailure() {
        when(leagueRepository.findIdsByStatusAndEndsAtLessThanEqual(LeagueStatus.STARTED, NOW))
                .thenReturn(List.of(1L, 2L));
        when(useCase.end(eq(1L), eq(NOW))).thenThrow(new IllegalStateException("실패"));
        when(useCase.end(eq(2L), eq(NOW))).thenReturn(true);

        new LeagueEndScheduler(leagueRepository, useCase, clock).endLeagues();

        verify(useCase).end(1L, NOW);
        verify(useCase).end(2L, NOW);
    }

    @Test
    @DisplayName("종료 시각이 지난 진행 중인 리그만 종료 대상이다")
    void onlyDueLeagues() {
        when(leagueRepository.findIdsByStatusAndEndsAtLessThanEqual(LeagueStatus.STARTED, NOW))
                .thenReturn(List.of(3L));

        new LeagueEndScheduler(leagueRepository, useCase, clock).catchUpOnStartup();

        verify(leagueRepository).findIdsByStatusAndEndsAtLessThanEqual(LeagueStatus.STARTED, NOW);
        verify(leagueRepository, never()).findIdsByStatus(any());
        verify(useCase).end(3L, NOW);
        verifyNoMoreInteractions(useCase);
    }
}
