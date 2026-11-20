package kr.ject.naspi.contents.infra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * OpenRouter chat completions 요청 본문 ({@code POST /chat/completions}).
 *
 * <p>쓰는 필드만 둔다. 응답 형식은 JSON Schema 로 강제하고({@code strict}), 이를 지원하는 공급자에게만
 * 보내도록 {@code provider.require_parameters} 를 켠다. 끄면 스키마를 무시하는 공급자로 라우팅될 수 있다.
 *
 * @param model 모델 ID
 * @param messages 대화 메시지
 * @param responseFormat 응답 형식
 * @param provider 공급자 라우팅 설정
 */
record OpenRouterRequest(
        String model,
        List<Message> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        Provider provider) {

    /**
     * JSON Schema 로 응답 형식을 강제하는 요청을 만든다.
     *
     * @param model 모델 ID
     * @param messages 대화 메시지
     * @param schemaName 스키마 이름
     * @param schema JSON Schema
     * @return 요청
     */
    static OpenRouterRequest withJsonSchema(String model, List<Message> messages,
                                            String schemaName, Map<String, Object> schema) {
        return new OpenRouterRequest(model, messages,
                new ResponseFormat("json_schema", new JsonSchema(schemaName, true, schema)),
                new Provider(true));
    }

    /**
     * 대화 메시지. 텍스트와 이미지를 함께 보낼 수 있도록 content 를 배열로 보낸다.
     *
     * @param role {@code system}, {@code user}, {@code assistant}
     * @param content 내용
     */
    record Message(String role, List<ContentPart> content) {

        static Message system(String text) {
            return new Message("system", List.of(ContentPart.text(text)));
        }

        static Message user(List<ContentPart> content) {
            return new Message("user", content);
        }

        static Message assistant(String text) {
            return new Message("assistant", List.of(ContentPart.text(text)));
        }
    }

    /**
     * 메시지 내용 조각. {@code type} 에 따라 {@code text} 나 {@code image_url} 중 하나만 채운다.
     *
     * @param type {@code text}, {@code image_url}
     * @param text 텍스트
     * @param imageUrl 이미지 주소
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ContentPart(String type, String text, @JsonProperty("image_url") ImageUrl imageUrl) {

        static ContentPart text(String text) {
            return new ContentPart("text", text, null);
        }

        static ContentPart image(String url) {
            return new ContentPart("image_url", null, new ImageUrl(url));
        }
    }

    /**
     * 이미지 주소.
     *
     * @param url 공개 URL 또는 data URL ({@code data:image/png;base64,...})
     */
    record ImageUrl(String url) {
    }

    /**
     * 응답 형식.
     *
     * @param type {@code json_schema}
     * @param jsonSchema 스키마
     */
    record ResponseFormat(String type, @JsonProperty("json_schema") JsonSchema jsonSchema) {
    }

    /**
     * 응답 JSON Schema.
     *
     * @param name 스키마 이름
     * @param strict 스키마를 엄격히 따르게 할지 여부
     * @param schema JSON Schema 본문
     */
    record JsonSchema(String name, boolean strict, Map<String, Object> schema) {
    }

    /**
     * 공급자 라우팅 설정.
     *
     * @param requireParameters 요청 파라미터(response_format 등)를 모두 지원하는 공급자에게만 보낼지 여부
     */
    record Provider(@JsonProperty("require_parameters") boolean requireParameters) {
    }
}
