package kr.ject.naspi.common.config;

import kr.ject.naspi.common.security.SessionCookieRefreshFilter;
import kr.ject.naspi.global.logging.WebLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서블릿 필터 등록. 직접 만든 필터는 모두 이곳에서 순서를 지정해 등록한다.
 *
 * <p>요청은 아래 순서로 필터를 지난다. 숫자가 작을수록 먼저 실행된다.
 *
 * <pre>
 * 1. HTTP 관측 필터              Spring Boot 자동 등록 (Ordered.HIGHEST_PRECEDENCE + 1)
 *                                트레이스를 시작한다. 이 뒤에서 남기는 로그에 traceId·spanId가 붙는다
 * 2. WebLoggingFilter            여기서 등록 (관측 필터 바로 뒤)
 *                                요청 메서드·URI·본문을 로그로 남긴다. 인증 실패 등으로 막힌 요청도 남도록
 *                                세션·보안 필터보다 앞에 둔다
 * 3. SessionRepositoryFilter     Spring Session 자동 등록 (SessionRepositoryFilter.DEFAULT_ORDER)
 *                                Redis에서 세션을 꺼내고, 응답할 때 저장한다
 * 4. SessionCookieRefreshFilter  여기서 등록 (DEFAULT_ORDER + 1)
 *                                유효한 세션이면 쿠키를 다시 내려보내 만료 시각을 연장한다
 * 5. Spring Security 필터 체인   SecurityConfig ({@code spring.security.filter.order}, 기본 -100)
 *                                인증 정보 로드, 카카오 로그인, 인가
 * </pre>
 */
@Configuration
public class FilterConfig {

    /** Spring Boot가 HTTP 관측 필터({@code ServerHttpObservationFilter})를 등록하는 순서. */
    private static final int OBSERVATION_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    /**
     * 요청 로그 필터를 HTTP 관측 필터 바로 뒤, 다른 필터보다 먼저 실행되도록 등록한다.
     *
     * <p>관측 필터보다 앞에 두면 요청·응답 로그에 traceId가 붙지 않는다.
     *
     * @param jsonMapper 요청 본문(JSON)을 읽기 좋은 형식으로 바꾸는 데 사용
     * @return 필터 등록 정보
     */
    @Bean
    public FilterRegistrationBean<WebLoggingFilter> webLoggingFilter(JsonMapper jsonMapper) {
        FilterRegistrationBean<WebLoggingFilter> registration =
                new FilterRegistrationBean<>(new WebLoggingFilter(jsonMapper));
        registration.setOrder(OBSERVATION_FILTER_ORDER + 1);
        return registration;
    }

    /**
     * 세션 쿠키 연장 필터를 Spring Session 필터 바로 뒤에 등록한다.
     *
     * @param cookieSerializer Spring Session이 사용하는 쿠키 설정 ({@code server.servlet.session.cookie.*})
     * @return 필터 등록 정보
     */
    @Bean
    public FilterRegistrationBean<SessionCookieRefreshFilter> sessionCookieRefreshFilter(
            CookieSerializer cookieSerializer) {
        FilterRegistrationBean<SessionCookieRefreshFilter> registration =
                new FilterRegistrationBean<>(new SessionCookieRefreshFilter(cookieSerializer));
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER + 1);
        return registration;
    }
}
