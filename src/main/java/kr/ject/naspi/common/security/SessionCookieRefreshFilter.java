package kr.ject.naspi.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 유효한 세션으로 들어온 요청마다 세션 쿠키를 다시 내려보내 쿠키 만료 시각을 연장한다.
 *
 * <p>Spring Session은 세션이 새로 만들어지거나 ID가 바뀔 때만 쿠키를 쓴다.
 * 그러면 Redis 세션은 요청마다 연장되어도 쿠키는 처음 발급 후 {@code max-age}가 지나면 사라진다.
 * 이 필터로 쿠키도 마지막 요청 기준으로 연장해 "마지막 요청 후 7일간 로그인 유지"를 맞춘다.
 *
 * <p>Spring Session 필터 뒤에서 실행되어야 한다 ({@link kr.ject.naspi.common.config.FilterConfig}).
 */
@RequiredArgsConstructor
public class SessionCookieRefreshFilter extends OncePerRequestFilter {

    private final CookieSerializer cookieSerializer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && request.isRequestedSessionIdValid()) {
            cookieSerializer.writeCookieValue(new CookieValue(request, response, session.getId()));
        }
        filterChain.doFilter(request, response);
    }
}
