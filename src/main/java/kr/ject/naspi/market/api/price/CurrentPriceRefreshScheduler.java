package kr.ject.naspi.market.api.price;

import java.time.Duration;
import kr.ject.naspi.market.application.CurrentPriceRefreshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 현재가 캐시를 주기적으로 갱신한다 ({@code naspi.market.current-price.refresh-interval}, 기본 1초).
 *
 * <ul>
 *   <li>전용 스레드 하나로 {@code fixedDelay} 실행하므로 갱신이 겹치지 않는다 (캐시에 쓰는 곳이 하나).</li>
 *   <li>한 번 실패해도 다음 주기는 계속 실행된다.</li>
 *   <li>주기가 0 이하이면 예약하지 않는다 (테스트).</li>
 * </ul>
 */
@Slf4j
@Component
class CurrentPriceRefreshScheduler implements SmartLifecycle {

    private final CurrentPriceRefreshService refreshService;
    private final Duration interval;
    private ThreadPoolTaskScheduler scheduler;
    private volatile boolean running;

    CurrentPriceRefreshScheduler(CurrentPriceRefreshService refreshService,
            @Value("${naspi.market.current-price.refresh-interval:1s}") Duration interval) {
        this.refreshService = refreshService;
        this.interval = interval;
    }

    @Override
    public void start() {
        running = true;
        if (interval.isZero() || interval.isNegative()) {
            log.info("현재가 캐시 갱신 주기가 없어 예약하지 않음: interval={}", interval);
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("current-price-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::refreshOnce, interval);
        log.info("현재가 캐시 갱신 예약: interval={}", interval);
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    void refreshOnce() {
        try {
            refreshService.refresh();
        } catch (RuntimeException e) {
            log.error("현재가 캐시 갱신 실패", e);
        }
    }
}
