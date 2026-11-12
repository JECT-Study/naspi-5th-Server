package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import kr.ject.naspi.global.config.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 테스트에서 시각을 앞으로 옮길 수 있는 KST 시계. 대기실 만료처럼 시간이 흘러야 확인할 수 있는 정책을 검증할 때 쓴다.
 *
 * <p>테스트 클래스에서 {@link Config}를 {@code @Import}하고 이 시계를 주입받아 {@link #advance}로 시간을 보낸다.
 */
final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
        this.instant = instant;
    }

    /**
     * 시각을 앞으로 옮긴다.
     *
     * @param duration 흘려보낼 시간
     */
    void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ClockConfig.KST;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return instant;
    }

    /**
     * 현재 시각에서 시작하는 {@link MutableClock}을 Clock 빈으로 등록한다.
     *
     * <p>DB는 일시를 마이크로초까지 반올림해 저장하므로, 저장한 일시가 시계보다 미세하게 늦어지지 않도록
     * 초 단위로 맞춰 시작한다.
     */
    @TestConfiguration
    static class Config {

        @Bean
        MutableClock clock() {
            return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }
}
