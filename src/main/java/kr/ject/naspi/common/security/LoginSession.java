package kr.ject.naspi.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 회원 로그인 상태를 세션에 저장하거나 지운다.
 *
 * <p>로그인하면 세션 유지 시간을 로그인 유지 기간({@code naspi.auth.login-session-timeout})으로 늘린다.
 * 로그인 전 세션은 기본 유지 시간({@code spring.session.timeout})만 유지되어 Redis에 오래 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class LoginSession {

    private static final List<SimpleGrantedAuthority> MEMBER_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));

    private final SecurityContextRepository securityContextRepository;
    private final AuthProperties authProperties;
    private final SecurityContextHolderStrategy holderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * 회원을 로그인 상태로 만든다.
     *
     * <p>세션 고정 공격을 막기 위해 세션 ID를 새로 발급한다.
     *
     * @param request 요청
     * @param response 응답
     * @param memberId 로그인할 회원 ID
     */
    public void login(HttpServletRequest request, HttpServletResponse response, long memberId) {
        request.getSession();
        request.changeSessionId();

        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginMember(memberId), null, MEMBER_AUTHORITIES));
        holderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        request.getSession().setMaxInactiveInterval(
                (int) authProperties.loginSessionTimeout().toSeconds());
    }

    /**
     * 세션에 저장된 인증 정보를 지운다. 세션의 다른 값은 유지한다.
     *
     * @param request 요청
     * @param response 응답
     */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext empty = holderStrategy.createEmptyContext();
        holderStrategy.setContext(empty);
        securityContextRepository.saveContext(empty, request, response);
    }
}
