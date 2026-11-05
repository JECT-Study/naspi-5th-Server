package kr.ject.naspi.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 계산에 사용할 {@link Clock}을 등록한다.
 *
 * <p>정책의 시간 기준이 KST이므로 Asia/Seoul 시간대를 사용한다.
 * 도메인·서비스 코드는 {@code LocalDateTime.now()} 대신 이 Clock을 주입받아 사용하고,
 * 테스트에서는 고정된 Clock으로 교체한다.
 */
@Configuration
public class ClockConfig {

    /** 서비스 기준 시간대. */
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * KST 기준 시스템 시계를 등록한다.
     *
     * @return KST 시스템 시계
     */
    @Bean
    public Clock clock() {
        return Clock.system(KST);
    }
}
