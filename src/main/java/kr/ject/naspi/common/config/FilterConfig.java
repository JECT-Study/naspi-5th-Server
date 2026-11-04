package kr.ject.naspi.common.config;

import kr.ject.naspi.common.security.SessionCookieRefreshFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * 서블릿 필터 등록. 직접 만든 필터는 모두 이곳에서 순서를 지정해 등록한다.
 *
 * <p>요청은 아래 순서로 필터를 지난다. 숫자가 작을수록 먼저 실행된다.
 *
 * <pre>
 * 1. SessionRepositoryFilter     Spring Session 자동 등록 (SessionRepositoryFilter.DEFAULT_ORDER)
 *                                Redis에서 세션을 꺼내고, 응답할 때 저장한다
 * 2. SessionCookieRefreshFilter  여기서 등록 (DEFAULT_ORDER + 1)
 *                                유효한 세션이면 쿠키를 다시 내려보내 만료 시각을 연장한다
 * 3. Spring Security 필터 체인   SecurityConfig ({@code spring.security.filter.order}, 기본 -100)
 *                                인증 정보 로드, 카카오 로그인, 인가
 * </pre>
 */
@Configuration
public class FilterConfig {

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
