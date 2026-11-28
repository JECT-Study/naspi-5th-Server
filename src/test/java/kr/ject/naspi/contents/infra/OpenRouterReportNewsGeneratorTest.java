package kr.ject.naspi.contents.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.ReportNewsGenerationResult;
import kr.ject.naspi.contents.domain.ReportNewsSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OpenRouterReportNewsGeneratorTest {

    private static final String URL = "https://openrouter.test/chat/completions";
    private static final String TITLE = "박민수, 신규 계약 체결";
    private static final String BODY = "신규 계약을 체결했다. 제보자 최지우";
    private static final ReportNewsSource SOURCE = new ReportNewsSource(
            "박민수", "최지우", "민수가 오늘 계약함",
            List.of("https://img.test/1.png", "https://img.test/2.png"));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private OpenRouterReportNewsGenerator generator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openrouter.test");
        server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterClient client = new OpenRouterClient(builder.build(), Duration.ZERO);
        OpenRouterProperties properties = new OpenRouterProperties(
                "https://openrouter.test", "key", "test/model",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO);
        generator = new OpenRouterReportNewsGenerator(client, jsonMapper, properties);
    }

    @Test
    @DisplayName("종목 이름·제보자 이름·제보 본문과 이미지를 보내고, 과거 공시 요약은 보내지 않는다")
    void requestFormat() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("test/model"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required.length()")
                        .value(4))
                .andExpect(jsonPath("$.provider.require_parameters").value(true))
                .andExpect(jsonPath("$.messages[-1].role").value("user"))
                .andExpect(jsonPath("$.messages[-1].content[0].text")
                        .value(containsString("<stock_name>박민수</stock_name>")))
                .andExpect(jsonPath("$.messages[-1].content[0].text")
                        .value(containsString("<reporter_name>최지우</reporter_name>")))
                .andExpect(jsonPath("$.messages[-1].content[0].text")
                        .value(containsString("민수가 오늘 계약함")))
                .andExpect(jsonPath("$.messages[-1].content[0].text")
                        .value(not(containsString("past_summaries"))))
                .andExpect(jsonPath("$.messages[-1].content[1].image_url.url")
                        .value("https://img.test/1.png"))
                .andExpect(jsonPath("$.messages[-1].content[2].image_url.url")
                        .value("https://img.test/2.png"))
                .andRespond(ok(output(TITLE, BODY)));

        generator.generate(SOURCE);

        server.verify();
    }

    @Test
    @DisplayName("작성 예시를 system 프롬프트 뒤, 실제 제보 앞에 user·assistant 대화로 넣는다")
    void examplesPrecedeRequest() {
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content[0].text")
                        .value(containsString("<stock_name>김철수</stock_name>")))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].content[0].text")
                        .value(startsWith("{\"publishable\":true,\"title\":")))
                .andExpect(jsonPath("$.messages[-1].content[0].text")
                        .value(containsString("민수가 오늘 계약함")))
                .andRespond(ok(output(TITLE, BODY)));

        generator.generate(SOURCE);

        server.verify();
    }

    @Test
    @DisplayName("생성 결과를 제목·본문·호재·악재로 반환한다")
    void generated() {
        server.expect(once(), requestTo(URL)).andRespond(ok(output(TITLE, BODY)));

        ReportNewsGenerationResult result = generator.generate(SOURCE);

        assertThat(result).isEqualTo(
                new ReportNewsGenerationResult.Generated(TITLE, BODY, NewsSentiment.POSITIVE));
    }

    @Test
    @DisplayName("기사 소재를 파악할 수 없으면 '제보 내용 부족'으로 미생성하고 재시도하지 않는다")
    void skippedIsNotRetried() {
        server.expect(once(), requestTo(URL)).andRespond(ok(Map.of(
                "publishable", false, "title", "", "body", "", "sentiment", "POSITIVE")));

        ReportNewsGenerationResult result = generator.generate(SOURCE);

        assertThat(result).isEqualTo(new ReportNewsGenerationResult.Skipped());
        assertThat(((ReportNewsGenerationResult.Skipped) result).reason())
                .isEqualTo("제보 내용 부족");
        server.verify();
    }

    @Test
    @DisplayName("제목이 종목 이름과 쉼표·공백으로 시작하지 않으면 출력 검증 실패로 재시도한다")
    void titleFormatFailureIsRetried() {
        server.expect(once(), requestTo(URL)).andRespond(ok(output("신규 계약 체결", BODY)));
        server.expect(once(), requestTo(URL))
                .andRespond(ok(output("박민수,신규 계약 체결", BODY)));
        server.expect(once(), requestTo(URL)).andRespond(ok(output(TITLE, BODY)));

        assertThat(generator.generate(SOURCE))
                .isInstanceOf(ReportNewsGenerationResult.Generated.class);
        server.verify();
    }

    @Test
    @DisplayName("본문이 `제보자 {제보자 이름}`으로 끝나지 않으면 재시도한다")
    void reporterMissingIsRetried() {
        server.expect(once(), requestTo(URL))
                .andRespond(ok(output(TITLE, "제보자 최지우가 알린 신규 계약을 체결했다")));
        server.expect(once(), requestTo(URL)).andRespond(ok(output(TITLE, BODY)));

        assertThat(generator.generate(SOURCE))
                .isInstanceOf(ReportNewsGenerationResult.Generated.class);
        server.verify();
    }

    @Test
    @DisplayName("제목 30자 초과는 재시도한다")
    void lengthFailureIsRetried() {
        server.expect(once(), requestTo(URL))
                .andRespond(ok(output("박민수, " + "가".repeat(30), BODY)));
        server.expect(once(), requestTo(URL)).andRespond(ok(output(TITLE, BODY)));

        assertThat(generator.generate(SOURCE))
                .isInstanceOf(ReportNewsGenerationResult.Generated.class);
        server.verify();
    }

    @Test
    @DisplayName("응답 형식이 틀리면(publishable 누락) 미생성으로 보지 않고 재시도한다")
    void missingPublishableIsRetried() {
        server.expect(once(), requestTo(URL)).andRespond(ok(Map.of("title", TITLE)));
        server.expect(once(), requestTo(URL)).andRespond(ok(output(TITLE, BODY)));

        assertThat(generator.generate(SOURCE))
                .isInstanceOf(ReportNewsGenerationResult.Generated.class);
        server.verify();
    }

    @Test
    @DisplayName("호재·악재 값이 올바르지 않으면 재시도한다")
    void invalidSentimentIsRetried() {
        server.expect(once(), requestTo(URL)).andRespond(ok(Map.of(
                "publishable", true, "title", TITLE, "body", BODY, "sentiment", "NEUTRAL")));
        server.expect(once(), requestTo(URL)).andRespond(ok(output(TITLE, BODY)));

        assertThat(generator.generate(SOURCE))
                .isInstanceOf(ReportNewsGenerationResult.Generated.class);
        server.verify();
    }

    @Test
    @DisplayName("오류가 계속되면 최초 요청 포함 3회 시도 후 실패를 반환하고, 미생성과 구분한다")
    void failsAfterThreeAttempts() {
        server.expect(times(3), requestTo(URL)).andRespond(withServerError());
        assertThat(generator.generate(SOURCE))
                .isInstanceOf(ReportNewsGenerationResult.Failed.class);
        server.verify();

        server.reset();
        server.expect(times(3), requestTo(URL)).andRespond(ok(output("제목 형식 오류", BODY)));
        assertThat(generator.generate(SOURCE))
                .isInstanceOf(ReportNewsGenerationResult.Failed.class);
        server.verify();
    }

    private Map<String, Object> output(String title, String body) {
        return Map.of("publishable", true, "title", title, "body", body,
                "sentiment", "POSITIVE");
    }

    private ResponseCreator ok(Map<String, Object> output) {
        String content = jsonMapper.writeValueAsString(output);
        String body = jsonMapper.writeValueAsString(Map.of("choices", List.of(Map.of(
                "message", Map.of("role", "assistant", "content", content),
                "finish_reason", "stop"))));
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
