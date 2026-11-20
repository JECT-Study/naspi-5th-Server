package kr.ject.naspi.global.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 작업({@code @Async})을 켠다. 오래 걸리는 후속 작업(예: 공시 기반 AI 뉴스 생성)을 요청 스레드와 분리한다.
 *
 * <p>{@code @Async}는 항상 전용 스레드 풀을 쓴다. 큐는 제한하지 않아 작업을 버리지 않고, 요청의 traceId가
 * 비동기 스레드 로그에 이어진다.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** 동시에 실행하는 비동기 작업 수. */
    private static final int POOL_SIZE = 4;

    /** 종료 시 실행 중인 작업을 기다리는 최대 시간(초). */
    private static final int AWAIT_TERMINATION_SECONDS = 20;

    @Override
    public Executor getAsyncExecutor() {
        return asyncTaskExecutor();
    }

    /**
     * {@code @Async} 전용 스레드 풀. 다른 곳에서 Executor로 주입받지 않도록 후보에서 뺀다.
     *
     * @return 비동기 작업 실행기
     */
    @Bean(autowireCandidate = false)
    public ThreadPoolTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        return executor;
    }
}
