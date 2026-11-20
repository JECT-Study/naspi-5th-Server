package kr.ject.naspi.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 요청이 들어올 때 요청 로그를, 응답을 내보낼 때 응답 로그를 각각 남긴다.
 *
 * <pre>
 * [REQUEST]  POST /api/members/me/name body: {name: 홍길동}
 * [RESPONSE] POST /api/members/me/name 200 body: {success: true, data: {name: 홍길동}}
 * </pre>
 *
 * <p>응답 로그에도 메서드·URI를 붙여 동시에 들어온 요청 사이에서 짝을 찾을 수 있게 한다.
 *
 * <ul>
 *   <li>요청 본문은 컨트롤러보다 먼저 읽어 로그를 남기고, {@link CachedBodyRequest}로 다시 읽을 수 있게 넘긴다.</li>
 *   <li>응답 본문은 {@link ContentCachingResponseWrapper}에 모았다가 로그를 남긴 뒤 클라이언트로 보낸다.</li>
 *   <li>JSON 본문은 {@code {key1: value1, key2: [a, b]}}처럼 따옴표 없이 남긴다.
 *       JSON이 아니거나 파싱에 실패하면 받은 그대로 남긴다.</li>
 *   <li>쿼리 문자열은 남기지 않는다. 카카오 로그인 콜백의 인가 코드처럼 민감한 값이 실릴 수 있다.</li>
 *   <li>Swagger UI·API 문서·H2 콘솔 요청은 로그를 남기지 않는다.</li>
 * </ul>
 *
 * <p>multipart(파일 업로드) 요청은 본문을 미리 읽으면 파일 파트를 파싱할 수 없으므로 감싸지 않고 본문을 생략한다.
 * 이미지 같은 바이너리 응답도 본문을 생략한다. 서명 URL처럼 그 자체가 접근 권한인 JSON 값({@code signedUrl})은
 * 가려서 남긴다. 본문 길이 제한은 아직 하지 않는다.
 * 요청 본문을 미리 읽으므로 폼({@code application/x-www-form-urlencoded}) 요청의
 * {@code getParameter}로는 본문 값을 읽을 수 없다. 현재 API는 JSON만 받는다.
 *
 * <p>트레이스를 시작하는 HTTP 관측 필터 바로 뒤, 다른 필터보다 먼저 실행되도록 등록한다
 * ({@link kr.ject.naspi.common.config.FilterConfig}). 그래서 로그에 traceId가 붙는다.
 */
@Slf4j
@RequiredArgsConstructor
public class WebLoggingFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PATH_PREFIXES =
            List.of("/swagger-ui", "/v3/api-docs", "/h2-console");

    /** 값을 로그에 남기지 않을 JSON 키. */
    private static final Set<String> MASKED_KEYS = Set.of("signedUrl");

    private static final String OMITTED = "(omitted)";

    private final JsonMapper jsonMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return EXCLUDED_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        HttpServletRequest loggedRequest = request;
        if (isMultipart(request.getContentType())) {
            log.info("[REQUEST] {} {} body: {}", request.getMethod(), request.getRequestURI(),
                    OMITTED);
        } else {
            CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
            loggedRequest = cachedRequest;
            log.info("[REQUEST] {} {} body: {}", request.getMethod(), request.getRequestURI(),
                    body(request.getContentType(), cachedRequest.body()));
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(loggedRequest, wrappedResponse);
        } finally {
            log.info("[RESPONSE] {} {} {} body: {}",
                    request.getMethod(), request.getRequestURI(), wrappedResponse.getStatus(),
                    body(wrappedResponse.getContentType(),
                            wrappedResponse.getContentAsByteArray()));
            wrappedResponse.copyBodyToResponse();
        }
    }

    private static boolean isMultipart(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private String body(String contentType, byte[] content) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return OMITTED;
        }
        String body = new String(content, StandardCharsets.UTF_8);
        if (body.isEmpty() || contentType == null || !contentType.contains("json")) {
            return body;
        }
        try {
            return format(jsonMapper.readTree(body));
        } catch (JacksonException e) {
            return body;
        }
    }

    private static String format(JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream()
                    .map(entry -> entry.getKey() + ": " + (MASKED_KEYS.contains(entry.getKey())
                            ? "***" : format(entry.getValue())))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        if (node.isArray()) {
            return node.values().stream()
                    .map(WebLoggingFilter::format)
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        return node.isNull() ? "null" : node.asString();
    }
}
