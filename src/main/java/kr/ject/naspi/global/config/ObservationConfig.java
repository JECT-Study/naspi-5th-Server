package kr.ject.naspi.global.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * 관측(트레이스 스팬·요청 타이머) 대상에서 뺄 요청을 정한다.
 */
@Configuration
public class ObservationConfig {

    /**
     * 헬스체크·actuator 요청에서는 관측을 시작하지 않는다.
     *
     * <p>HTTP 요청 관측만 막으면 그 요청 안에서 Spring Security가 만드는 관측이 부모 없는 트레이스로 남는다.
     * 부모가 관측되지 않아도 자식 관측은 자동으로 꺼지지 않기 때문이다. 일반 요청의 Security 관측은 항상
     * HTTP 요청 관측을 부모로 가지므로, 관측 중인 부모가 없는 Security 관측을 함께 막는다. HTTP 요청 관측이
     * 아예 없는 management 포트의 요청도 이 규칙으로 걸러진다.
     *
     * @return 관측 여부 판정
     */
    @Bean
    public ObservationPredicate excludeProbeObservations() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext server) {
                return !isExcluded(server.getCarrier().getRequestURI());
            }
            return !name.startsWith("spring.security.")
                    || (context.getParentObservation() instanceof Observation parent
                    && !parent.isNoop());
        };
    }

    private static boolean isExcluded(String path) {
        return path.equals("/livez") || path.equals("/readyz")
                || path.equals("/actuator") || path.startsWith("/actuator/");
    }
}
