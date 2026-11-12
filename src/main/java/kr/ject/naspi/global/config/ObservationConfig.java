package kr.ject.naspi.global.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 관측(트레이스 스팬·요청 타이머) 대상에서 뺄 요청을 정한다.
 */
@Configuration
public class ObservationConfig {

    /**
     * 헬스체크·actuator 요청에서는 관측을 시작하지 않는다.
     *
     * <p>HTTP 요청 관측만 막으면 그 요청 안에서 만들어지는 관측(Spring Security, 헬스체크의 DB 연결 확인 등)이
     * 부모 없는 트레이스로 남는다. 부모가 관측되지 않아도 자식 관측은 자동으로 꺼지지 않기 때문이다. 그래서
     * 부모가 막힌 관측은 함께 막는다.
     *
     * <p>management 포트에는 HTTP 요청 관측이 없어 부모 자체가 없다. 이때 Security 관측은 막고(일반 요청의
     * Security 관측은 항상 HTTP 요청 관측을 부모로 가진다), 나머지는 현재 처리 중인 요청이 제외 대상일 때만
     * 막는다. 스케줄러·이벤트 리스너처럼 요청 밖에서 시작된 관측은 그대로 남는다.
     *
     * @return 관측 여부 판정
     */
    @Bean
    public ObservationPredicate excludeProbeObservations() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext server) {
                return !isExcluded(server.getCarrier().getRequestURI());
            }
            if (context.getParentObservation() instanceof Observation parent) {
                return !parent.isNoop();
            }
            return !name.startsWith("spring.security.") && !isHandlingExcludedRequest();
        };
    }

    private static boolean isHandlingExcludedRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes web
                && isExcluded(web.getRequest().getRequestURI());
    }

    private static boolean isExcluded(String path) {
        return path.equals("/livez") || path.equals("/readyz")
                || path.equals("/actuator") || path.startsWith("/actuator/");
    }
}
