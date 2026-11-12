package kr.ject.naspi.league.application;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 기한이 지난 대기실을 만료시킨다.
 *
 * <p>여러 서버에서 동시에 실행되어도 만료 대상 리그를 잠그고 처리하므로 같은 리그를 두 번 만료시키지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LeagueExpirationScheduler {

    private final LeagueExpirationService leagueExpirationService;

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    void expireOverdueWaitingRooms() {
        int expired = leagueExpirationService.expireOverdueWaitingRooms();
        if (expired > 0) {
            log.info("기한이 지난 대기실 {}개를 만료시켰습니다.", expired);
        }
    }
}
