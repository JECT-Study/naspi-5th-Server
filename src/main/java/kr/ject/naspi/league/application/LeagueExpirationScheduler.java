package kr.ject.naspi.league.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 오전 9시(KST)에 기한이 지난 대기실을 만료시킨다. 기한이 지났어도 만료 처리 전까지는 대기실로 유지된다.
 *
 * <p>여러 서버에서 동시에 실행되어도 만료 대상 리그를 잠그고 처리하므로 같은 리그를 두 번 만료시키지 않는다.
 */
// TODO(ND-107): 이중화 환경에서는 모든 서버가 동시에 실행한다. 행 잠금으로 같은 리그를 두 번 만료시키지는 않지만,
//  잠금을 기다린 쪽이 이미 만료된 리그를 받으면 League#expire가 IllegalStateException을 던져 그 실행 전체가
//  롤백될 수 있다. 이미 만료된 리그는 건너뛰도록 하거나 분산 락(ShedLock 등)으로 한 서버만 실행하도록 검토한다.
@Slf4j
@Component
@RequiredArgsConstructor
class LeagueExpirationScheduler {

    private final LeagueExpirationService leagueExpirationService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    void expireOverdueWaitingRooms() {
        int expired = leagueExpirationService.expireOverdueWaitingRooms();
        if (expired > 0) {
            log.info("기한이 지난 대기실 {}개를 만료시켰습니다.", expired);
        }
    }
}
