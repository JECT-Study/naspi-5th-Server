package kr.ject.naspi.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 계산에 사용할 {@link Clock}을 등록한다.
 *
 * <p>시간대는 JVM 기본 시간대(실행 시 {@code -Duser.timezone})를 따른다.
 * 도메인·서비스 코드는 {@code LocalDateTime.now()} 대신 이 Clock을 주입받아 사용하고,
 * 테스트에서는 고정된 Clock으로 교체한다.
 */
@Configuration
public class ClockConfig {

    /** 서비스 기준 시간대. */
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * JVM 기본 시간대 기준 시스템 시계를 등록한다.
     *
     * @return 시스템 시계
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
