package kr.ject.naspi.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/**
 * 카카오 로그인을 마친 사용자를 로그인을 시작한 프론트 출처로 돌려보낸다.
 *
 * <p>로그인 시작({@code GET /oauth2/authorization/kakao})은 페이지 이동이라 {@code Origin} 헤더가 없고,
 * 로그인 완료 요청은 카카오에서 돌아오는 콜백이라 프론트 정보가 없다.
 * 그래서 시작할 때 {@code Referer}의 출처를 세션에 저장해 두고, 끝날 때 꺼내 그 출처의 경로로 이동시킨다.
 *
 * <p>오픈 리다이렉트를 막기 위해 CORS 허용 출처({@code naspi.cors.allowed-origins})에 있는 출처만 저장한다.
 * 저장된 출처가 없으면(직접 접속, 허용되지 않은 출처) 허용 출처의 첫 번째로 이동한다.
 * 빈 등록은 {@link SecurityConfig}에서 한다.
 */
public class LoginRedirect {

    /** 세션 속성 이름. */
    static final String ATTRIBUTE = LoginRedirect.class.getName() + ".ORIGIN";

    private final List<String> allowedOrigins;

    /**
     * 로그인 리다이렉트를 만든다.
     *
     * @param corsProperties 이동을 허용할 프론트 출처 (첫 번째가 기본 출처)
     */
    public LoginRedirect(CorsProperties corsProperties) {
        if (corsProperties.allowedOrigins() == null || corsProperties.allowedOrigins().isEmpty()) {
            throw new IllegalStateException("naspi.cors.allowed-origins 가 비어 있습니다.");
        }
        this.allowedOrigins = List.copyOf(corsProperties.allowedOrigins());
    }

    /**
     * 로그인을 시작한 프론트 출처를 세션에 저장한다. 허용되지 않은 출처이거나 알 수 없으면 이전 값을 지운다.
     *
     * @param request 로그인 시작 요청
     */
    public void saveOrigin(HttpServletRequest request) {
        Optional<String> origin = originOf(request.getHeader(HttpHeaders.REFERER))
                .filter(allowedOrigins::contains);
        if (origin.isPresent()) {
            request.getSession().setAttribute(ATTRIBUTE, origin.get());
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }

    /**
     * 저장된 프론트 출처의 경로로 이동시키고, 저장된 출처를 지운다.
     *
     * @param request 요청
     * @param response 응답
     * @param path 이동할 프론트 경로 (예: {@code /signup})
     * @throws IOException 리다이렉트 응답을 쓰지 못한 경우
     */
    public void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        String origin = allowedOrigins.getFirst();
        HttpSession session = request.getSession(false);
        if (session != null) {
            if (session.getAttribute(ATTRIBUTE) instanceof String saved
                    && allowedOrigins.contains(saved)) {
                origin = saved;
            }
            session.removeAttribute(ATTRIBUTE);
        }
        response.sendRedirect(origin + path);
    }

    private static Optional<String> originOf(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return Optional.empty();
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return Optional.of(uri.getScheme() + "://" + uri.getHost() + port);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
