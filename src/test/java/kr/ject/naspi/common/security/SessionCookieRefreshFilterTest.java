package kr.ject.naspi.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.DefaultCookieSerializer;

class SessionCookieRefreshFilterTest {

    private static final int SEVEN_DAYS = 7 * 24 * 60 * 60;

    private final SessionCookieRefreshFilter filter = new SessionCookieRefreshFilter(serializer());

    @Test
    @DisplayName("로그인 유지: 유효한 세션으로 요청하면 세션 쿠키를 다시 내려보내 만료 시각을 연장한다")
    void refreshCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        request.setRequestedSessionIdValid(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("SESSION=")
                .contains("Max-Age=" + SEVEN_DAYS)
                .contains("HttpOnly");
    }

    @Test
    @DisplayName("세션이 없는 요청에는 세션 쿠키를 내려보내지 않는다")
    void noSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    private static DefaultCookieSerializer serializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieMaxAge(SEVEN_DAYS);
        serializer.setUseHttpOnlyCookie(true);
        return serializer;
    }
}
