package kr.ject.naspi.league.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 자정(KST)에 기한이 지난 대기실을 만료시킨다.
 *
 * <p>실행 사이에 기한이 지난 대기실은 조회·입장 시점의 만료 판정({@code League#isExpired})이 막으므로,
 * 하루 한 번 상태를 저장하는 것으로 충분하다.
 *
 * <p>여러 서버에서 동시에 실행되어도 만료 대상 리그를 잠그고 처리하므로 같은 리그를 두 번 만료시키지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LeagueExpirationScheduler {

    private final LeagueExpirationService leagueExpirationService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    void expireOverdueWaitingRooms() {
        int expired = leagueExpirationService.expireOverdueWaitingRooms();
        if (expired > 0) {
            log.info("기한이 지난 대기실 {}개를 만료시켰습니다.", expired);
        }
    }
}
