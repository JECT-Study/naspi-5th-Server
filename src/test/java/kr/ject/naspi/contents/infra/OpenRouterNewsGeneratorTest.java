package kr.ject.naspi.contents.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import kr.ject.naspi.contents.domain.NewsGenerationResult;
import kr.ject.naspi.contents.domain.NewsSentiment;
import kr.ject.naspi.contents.domain.NewsSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OpenRouterNewsGeneratorTest {

    private static final String URL = "https://openrouter.test/chat/completions";
    private static final NewsSource SOURCE = new NewsSource(
            "오늘 헬스장 등록", "3개월 끊었습니다", List.of("https://img.test/1.png"),
            List.of("다이어트 선언", "야식 끊기 3일차"));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private OpenRouterNewsGenerator generator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openrouter.test");
        server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterClient client = new OpenRouterClient(builder.build(), Duration.ZERO);
        OpenRouterProperties properties = new OpenRouterProperties(
                "https://openrouter.test", "key", "test/model",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ZERO);
        generator = new OpenRouterNewsGenerator(client, jsonMapper, properties);
    }

    @Test
    @DisplayName("응답 형식을 JSON Schema(strict)로 지정하고, 이를 지원하는 공급자에게만 보내며, "
            + "과거 공시 요약(오래된 순)과 이미지를 함께 보낸다")
    void requestFormat() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("test/model"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required.length()")
                        .value(6))
                .andExpect(jsonPath("$.provider.require_parameters").value(true))
                .andExpect(jsonPath("$.messages[-1].role").value("user"))
                .andExpect(jsonPath("$.messages[-1].content[0].text")
                        .value(containsString("1. 다이어트 선언\n2. 야식 끊기 3일차")))
                .andExpect(jsonPath("$.messages[-1].content[1].image_url.url")
                        .value("https://img.test/1.png"))
                .andRespond(ok(output("헬스장 3개월 등록", "체력 개선 기대감")));

        generator.generate(SOURCE);

        server.verify();
    }

    @Test
    @DisplayName("결과의 통일성을 위해 작성 예시를 system 프롬프트 뒤, 실제 공시 앞에 user·assistant 대화로 넣는다")
    void examplesPrecedeRequest() {
        server.expect(once(), requestTo(URL))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content[0].text")
                        .value(containsString("오늘 헬스장 등록함")))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].content[0].text")
                        .value(startsWith("{\"publishable\":true,\"reason\":\"\",\"title\":")))
                .andExpect(jsonPath("$.messages[-1].content[0].text")
                        .value(containsString("3개월 끊었습니다")))
                .andRespond(ok(output("헬스장 3개월 등록", "체력 개선 기대감")));

        generator.generate(SOURCE);

        server.verify();
    }

    @Test
    @DisplayName("생성 결과를 제목·본문·호악재·이번 공시 요약본으로 반환한다")
    void generated() {
        server.expect(once(), requestTo(URL))
                .andRespond(ok(output("헬스장 3개월 등록", "체력 개선 기대감")));

        NewsGenerationResult result = generator.generate(SOURCE);

        assertThat(result).isEqualTo(new NewsGenerationResult.Generated(
                "헬스장 3개월 등록", "체력 개선 기대감", NewsSentiment.POSITIVE, "헬스장 3개월 등록"));
    }

    @Test
    @DisplayName("기사 소재가 없어 미생성이면 재시도하지 않는다")
    void skippedIsNotRetried() {
        server.expect(once(), requestTo(URL)).andRespond(ok(Map.of(
                "publishable", false, "reason", "판독 불가", "title", "", "body", "",
                "sentiment", "POSITIVE", "summary", "")));

        NewsGenerationResult result = generator.generate(SOURCE);

        assertThat(result).isEqualTo(new NewsGenerationResult.Skipped("판독 불가"));
        server.verify();
    }

    @Test
    @DisplayName("출력 검증 실패(제목 30자 초과)는 재시도한다")
    void validationFailureIsRetried() {
        server.expect(once(), requestTo(URL))
                .andRespond(ok(output("가".repeat(31), "본문")));
        server.expect(once(), requestTo(URL))
                .andRespond(ok(output("제목", "본문")));

        NewsGenerationResult result = generator.generate(SOURCE);

        assertThat(result).isInstanceOf(NewsGenerationResult.Generated.class);
        server.verify();
    }

    @Test
    @DisplayName("응답 형식이 틀리면(publishable 누락) 미생성으로 보지 않고 재시도한다")
    void missingPublishableIsRetried() {
        server.expect(once(), requestTo(URL)).andRespond(ok(Map.of("reason", "")));
        server.expect(once(), requestTo(URL))
                .andRespond(ok(output("제목", "본문")));

        assertThat(generator.generate(SOURCE)).isInstanceOf(NewsGenerationResult.Generated.class);
        server.verify();
    }

    @Test
    @DisplayName("호출 오류가 계속되면 최초 요청 포함 3회 시도 후 실패를 반환한다")
    void failsAfterThreeAttempts() {
        server.expect(times(3), requestTo(URL)).andRespond(withServerError());

        NewsGenerationResult result = generator.generate(SOURCE);

        assertThat(result).isInstanceOf(NewsGenerationResult.Failed.class);
        server.verify();
    }

    private Map<String, Object> output(String title, String body) {
        return Map.of("publishable", true, "reason", "", "title", title, "body", body,
                "sentiment", "POSITIVE", "summary", title);
    }

    private ResponseCreator ok(Map<String, Object> output) {
        String content = jsonMapper.writeValueAsString(output);
        String body = jsonMapper.writeValueAsString(Map.of("choices", List.of(Map.of(
                "message", Map.of("role", "assistant", "content", content),
                "finish_reason", "stop"))));
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
