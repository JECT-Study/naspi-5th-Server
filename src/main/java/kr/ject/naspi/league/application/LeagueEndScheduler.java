package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 09:00(KST)에 종료 시각이 지난 진행 중인 리그를 종료한다. 서버가 09:00에 내려가 있어 놓친 종료는 기동 직후
 * 보정한다.
 *
 * <p>리그마다 별도 트랜잭션으로 처리하며, 한 리그가 실패해도 나머지는 계속 처리한다. 실패한 리그는 진행 중 상태로
 * 남아 다음 실행에서 다시 처리된다. 같은 시각에 도는 거래일 전환과는 리그 락으로 직렬화되고, 종료 처리가 밀린 전환을
 * 스스로 보정하므로 두 스케줄러의 실행 순서에 의존하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty(name = "naspi.scheduling.enabled", matchIfMissing = true)
class LeagueEndScheduler {

    private final LeagueRepository leagueRepository;
    private final LeagueEndUseCase leagueEndUseCase;
    private final Clock clock;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    void endLeagues() {
        endAll();
    }

    @EventListener(ApplicationReadyEvent.class)
    void catchUpOnStartup() {
        endAll();
    }

    void endAll() {
        LocalDateTime now = LocalDateTime.now(clock);
        int ended = 0;
        for (long leagueId : leagueRepository.findIdsByStatusAndEndsAtLessThanEqual(
                LeagueStatus.STARTED, now)) {
            try {
                if (leagueEndUseCase.end(leagueId, now)) {
                    ended++;
                }
            } catch (RuntimeException e) {
                log.error("리그 종료에 실패했습니다. leagueId={}", leagueId, e);
            }
        }
        if (ended > 0) {
            log.info("리그 종료 {}건을 처리했습니다.", ended);
        }
    }
}
