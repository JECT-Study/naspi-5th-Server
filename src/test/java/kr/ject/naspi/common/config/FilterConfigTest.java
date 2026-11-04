package kr.ject.naspi.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import kr.ject.naspi.common.security.AuthProperties;
import kr.ject.naspi.common.security.SessionCookieRefreshFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.test.context.ActiveProfiles;

/**
 * 필터 등록 순서와 application.yaml의 세션 쿠키·로그인 유지 설정이 실제로 적용되는지 검증한다.
 *
 * <p>쿠키 설정({@code server.servlet.session.cookie.*})은 내장 서버로 실행할 때 적용되므로 내장 서버로 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FilterConfigTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CookieSerializer cookieSerializer;

    @Autowired
    private AuthProperties authProperties;

    @Test
    @DisplayName("세션 쿠키 연장 필터는 Spring Session 필터 뒤, Spring Security 필터 앞에서 실행된다")
    void filterOrder() {
        Map<String, AbstractFilterRegistrationBean> registrations =
                context.getBeansOfType(AbstractFilterRegistrationBean.class);
        int session = registrations.get("sessionRepositoryFilterRegistration").getOrder();
        int cookieRefresh = registrations.values().stream()
                .filter(r -> r.getFilterName().equals(
                        SessionCookieRefreshFilter.class.getSimpleName())
                        || r.getFilterName().equals("sessionCookieRefreshFilter"))
                .findFirst().orElseThrow().getOrder();
        int security = registrations.get("securityFilterChainRegistration").getOrder();

        assertThat(cookieRefresh).isGreaterThan(session).isLessThan(security);
    }

    @Test
    @DisplayName("로그인 유지 기간은 7일이고, 세션 쿠키도 브라우저를 닫아도 7일간 유지된다")
    void sevenDays() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieSerializer.writeCookieValue(
                new CookieValue(new MockHttpServletRequest(), response, "session-id"));

        assertThat(authProperties.loginSessionTimeout().toDays()).isEqualTo(7);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("Max-Age=604800")
                .contains("HttpOnly");
    }
}
