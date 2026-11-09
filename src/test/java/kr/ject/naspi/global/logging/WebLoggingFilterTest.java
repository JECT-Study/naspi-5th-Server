package kr.ject.naspi.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class WebLoggingFilterTest {

    private final WebLoggingFilter filter = new WebLoggingFilter(JsonMapper.builder().build());

    private final Logger logger = (Logger) LoggerFactory.getLogger(WebLoggingFilter.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("요청 로그는 컨트롤러 실행 전에, 응답 로그는 응답을 내보낼 때 따로 남긴다")
    void logRequestThenResponse() throws Exception {
        String requestBody = "{\"name\":\"홍길동\"}";
        String responseBody = "{\"success\":true,\"data\":{\"name\":\"홍길동\"}}";
        MockHttpServletRequest request = jsonRequest("/api/members/me/name", requestBody);
        request.setQueryString("code=secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> readBody = new AtomicReference<>();
        List<String> logsBeforeController = new ArrayList<>();

        filter.doFilter(request, response, (req, res) -> {
            logsBeforeController.addAll(messages());
            readBody.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(res, responseBody);
        });

        assertThat(logsBeforeController)
                .containsExactly("[REQUEST] POST /api/members/me/name body: {name: 홍길동}");
        assertThat(messages()).containsExactly(
                "[REQUEST] POST /api/members/me/name body: {name: 홍길동}",
                "[RESPONSE] POST /api/members/me/name 200 "
                        + "body: {success: true, data: {name: 홍길동}}");
        assertThat(readBody.get()).isEqualTo(requestBody);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(responseBody);
    }

    @Test
    @DisplayName("컨트롤러는 미리 읽힌 요청 본문을 getReader로도 그대로 읽는다")
    void readBodyWithReader() throws Exception {
        AtomicReference<String> readBody = new AtomicReference<>();

        filter.doFilter(jsonRequest("/api/test", "{\"a\":1}"), new MockHttpServletResponse(),
                (req, res) -> readBody.set(req.getReader().readLine()));

        assertThat(readBody.get()).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("중첩된 JSON 본문도 따옴표 없이 key: value 형식으로 남긴다")
    void formatNestedJson() throws Exception {
        String body = "{\"name\":\"홍길동\",\"tags\":[\"a\",\"b\"],"
                + "\"price\":{\"amount\":10000,\"memo\":null}}";

        filter.doFilter(jsonRequest("/api/test", body), new MockHttpServletResponse(),
                (req, res) -> {});

        assertThat(messages()).containsExactly(
                "[REQUEST] POST /api/test body: "
                        + "{name: 홍길동, tags: [a, b], price: {amount: 10000, memo: null}}",
                "[RESPONSE] POST /api/test 200 body: ");
    }

    @Test
    @DisplayName("JSON이 아닌 본문은 받은 그대로 남긴다")
    void rawText() throws Exception {
        MockHttpServletRequest request = jsonRequest("/api/test", "plain text");
        request.setContentType(MediaType.TEXT_PLAIN_VALUE);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        assertThat(messages()).first().isEqualTo("[REQUEST] POST /api/test body: plain text");
    }

    @Test
    @DisplayName("Swagger UI 요청은 로그를 남기지 않는다")
    void skipSwagger() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/swagger-ui/index.html");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        assertThat(messages()).isEmpty();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static MockHttpServletRequest jsonRequest(String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static void writeJson(ServletResponse res, String body) throws IOException {
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
