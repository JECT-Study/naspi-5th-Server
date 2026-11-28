package kr.ject.naspi.contents.infra;

import static kr.ject.naspi.contents.infra.AiResources.read;
import static kr.ject.naspi.contents.infra.AiResources.sentiment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.contents.domain.ReportNewsGenerationResult;
import kr.ject.naspi.contents.domain.ReportNewsGenerator;
import kr.ject.naspi.contents.domain.ReportNewsSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenRouter 로 제보 기반 AI 뉴스를 생성한다.
 *
 * <p>프롬프트는 {@code ai/report-news/system-prompt.txt}, 응답 형식은
 * {@code ai/report-news/response-schema.json}, 작성 예시는 {@code ai/report-news/examples.json} 에 둔다.
 * 응답 형식이 바뀌면 {@link Output} 과 예시도 함께 바꾼다.
 *
 * <p>작성 예시는 실제 요청 앞에 user·assistant 대화로 넣는다(few-shot).
 */
@Slf4j
@Component
class OpenRouterReportNewsGenerator implements ReportNewsGenerator {

    private static final String SCHEMA_NAME = "report_news";

    private final OpenRouterClient client;
    private final JsonMapper jsonMapper;
    private final String model;
    private final Map<String, Object> responseSchema;
    /** system 프롬프트와 작성 예시 대화. 모든 요청에 같은 순서로 앞에 붙는다. */
    private final List<OpenRouterRequest.Message> leadingMessages;

    OpenRouterReportNewsGenerator(OpenRouterClient client, JsonMapper jsonMapper,
                                  OpenRouterProperties properties) {
        this.client = client;
        this.jsonMapper = jsonMapper;
        this.model = properties.model();
        this.responseSchema = jsonMapper.readValue(read("ai/report-news/response-schema.json"),
                new TypeReference<>() {});
        List<OpenRouterRequest.Message> messages = new ArrayList<>();
        messages.add(OpenRouterRequest.Message.system(read("ai/report-news/system-prompt.txt")));
        messages.addAll(exampleMessages(read("ai/report-news/examples.json")));
        this.leadingMessages = List.copyOf(messages);
    }

    /**
     * 작성 예시를 user·assistant 대화로 바꾼다. 예시 출력도 실제 응답과 같은 검증을 통과해야 한다
     * (잘못된 예시를 넣으면 애플리케이션 시작에 실패한다).
     */
    private List<OpenRouterRequest.Message> exampleMessages(String json) {
        List<Example> examples = jsonMapper.readValue(json, new TypeReference<>() {});
        List<OpenRouterRequest.Message> messages = new ArrayList<>();
        for (Example example : examples) {
            String output = jsonMapper.writeValueAsString(example.output());
            toResult(example.input(), output);
            messages.add(OpenRouterRequest.Message.user(userContent(example.input())));
            messages.add(OpenRouterRequest.Message.assistant(output));
        }
        return messages;
    }

    @Override
    public ReportNewsGenerationResult generate(ReportNewsSource source) {
        // system 프롬프트 + 작성 예시
        List<OpenRouterRequest.Message> messages = new ArrayList<>(leadingMessages);
        // 이번 제보
        messages.add(OpenRouterRequest.Message.user(userContent(source)));
        OpenRouterRequest request = OpenRouterRequest.withJsonSchema(model, messages,
                SCHEMA_NAME, responseSchema);
        try {
            // 실패 시 client 안에서 최대 3회 시도
            return client.complete(request, content -> toResult(source, content));
        } catch (RuntimeException e) {
            log.warn("AI 제보 뉴스 생성 실패", e);
            return new ReportNewsGenerationResult.Failed(e.getMessage());
        }
    }

    private static List<OpenRouterRequest.ContentPart> userContent(ReportNewsSource source) {
        List<OpenRouterRequest.ContentPart> parts = new ArrayList<>();
        parts.add(OpenRouterRequest.ContentPart.text("""
                <report>
                <stock_name>%s</stock_name>
                <reporter_name>%s</reporter_name>
                <body>%s</body>
                <image_count>%d</image_count>
                </report>""".formatted(source.stockName(), source.reporterName(),
                source.body(), source.imageUrls().size())));
        source.imageUrls().forEach(url -> parts.add(OpenRouterRequest.ContentPart.image(url)));
        return parts;
    }

    /**
     * AI 응답(JSON 문자열)을 결과로 바꾼다. 형식이 틀리거나 검증에 실패하면 예외를 던져 재시도하게 한다.
     */
    ReportNewsGenerationResult toResult(ReportNewsSource source, String content) {
        Output output = jsonMapper.readValue(content, Output.class);
        if (output.publishable() == null) {
            throw new IllegalArgumentException("publishable 값이 없습니다: " + content);
        }
        if (!output.publishable()) {
            return new ReportNewsGenerationResult.Skipped();
        }
        return ReportNewsGenerationResult.Generated.of(source, output.title(), output.body(),
                sentiment(output.sentiment()));
    }

    /**
     * {@code response-schema.json} 에 맞춘 AI 응답. 미생성이면 publishable 만 의미가 있다.
     * publishable 은 누락을 실패로 보기 위해 {@link Boolean} 으로 받는다 (기본값 false 면 미생성으로 오인한다).
     * 예시 출력을 직렬화할 때 스키마와 같은 순서가 되도록 순서를 고정한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"publishable", "title", "body", "sentiment"})
    record Output(Boolean publishable, String title, String body, String sentiment) {
    }

    /**
     * {@code examples.json} 의 작성 예시 하나.
     *
     * @param input 예시 제보
     * @param output 예시 제보에 대한 모범 응답
     */
    record Example(ReportNewsSource input, Output output) {
    }
}
