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
 * 매일 09:00(KST)에 진행 중인 리그를 다음 거래일로 전환한다. 서버가 09:00에 내려가 있어 놓친 전환은 기동 직후 보정한다.
 *
 * <p>리그마다 별도 트랜잭션으로 처리하며, 한 리그가 실패해도 나머지는 계속 처리한다. 실패한 리그는 다음 실행에서
 * 밀린 전환을 다시 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty(name = "naspi.scheduling.enabled", matchIfMissing = true)
class TradingDayChangeScheduler {

    private final LeagueRepository leagueRepository;
    private final TradingDayChangeUseCase tradingDayChangeUseCase;
    private final Clock clock;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    void changeTradingDays() {
        changeAll();
    }

    @EventListener(ApplicationReadyEvent.class)
    void catchUpOnStartup() {
        changeAll();
    }

    void changeAll() {
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = 0;
        for (long leagueId : leagueRepository.findIdsByStatus(LeagueStatus.STARTED)) {
            try {
                changed += tradingDayChangeUseCase.change(leagueId, now);
            } catch (RuntimeException e) {
                log.error("거래일 전환에 실패했습니다. leagueId={}", leagueId, e);
            }
        }
        if (changed > 0) {
            log.info("거래일 전환 {}건을 처리했습니다.", changed);
        }
    }
}
