package kr.ject.naspi.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 주기 작업({@code @Scheduled})을 켠다. 대기실 만료처럼 정해진 시각이 지나면 상태를 바꿔야 하는 작업에 쓴다.
 *
 * <p>WebSocket 브로커 스케줄러(heartbeat)를 함께 쓰지 않도록 주기 작업 전용 스케줄러를 따로 둔다.
 *
 * <p>{@code naspi.scheduling.enabled=false}이면 주기 작업을 실행하지 않는다. 테스트에서 주기 작업이
 * 테스트 데이터·관측 기록에 끼어들지 않도록 끈다.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "naspi.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduledTaskScheduler());
    }

    /**
     * 주기 작업 전용 스레드 풀. 다른 곳에서 TaskScheduler로 주입받지 않도록 후보에서 뺀다.
     *
     * @return 주기 작업 스케줄러
     */
    @Bean(autowireCandidate = false)
    public ThreadPoolTaskScheduler scheduledTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}
