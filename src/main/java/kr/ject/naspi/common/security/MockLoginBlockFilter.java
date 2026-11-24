package kr.ject.naspi.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.global.exception.ErrorCode;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 허용 프로필이 아니면 모킹 로그인(테스트 전용) 요청을 404로 끝낸다.
 *
 * <p>운영에서 실수로 열리지 않도록 "운영이 아니면 허용"이 아니라 허용할 프로필을 나열한다. 프로필을 지정하지 않으면
 * production으로 동작하므로({@code spring.profiles.default}) 막힌다. production이 함께 지정된 경우도 막는다.
 *
 * <p>모든 프로필에서 {@link #PATH}에만 등록한다 ({@link kr.ject.naspi.common.config.FilterConfig}).
 */
public class MockLoginBlockFilter extends OncePerRequestFilter {

    /** 모킹 로그인 경로. */
    public static final String PATH = "/api/auth/mock-login";

    /** 모킹 로그인을 허용하는 프로필. */
    public static final String ALLOWED_PROFILES =
            "(local | development | test) & !production";

    private final boolean allowed;
    private final JsonMapper jsonMapper;

    /**
     * 현재 프로필에서 모킹 로그인을 허용할지 정한다.
     *
     * @param environment 활성 프로필
     * @param jsonMapper 404 응답 본문 변환
     */
    public MockLoginBlockFilter(Environment environment, JsonMapper jsonMapper) {
        this.allowed = environment.acceptsProfiles(Profiles.of(ALLOWED_PROFILES));
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), Result.error(ErrorCode.NOT_FOUND));
    }
}
