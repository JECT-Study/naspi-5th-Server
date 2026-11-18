package kr.ject.naspi.contents.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * OpenRouter chat completions 응답 본문. 쓰는 필드만 읽는다.
 *
 * @param choices 생성 결과
 * @param error 오류. HTTP 200 이어도 공급자 오류가 여기에 담겨 올 수 있다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenRouterResponse(List<Choice> choices, Error error) {

    /**
     * 생성 결과 하나.
     *
     * @param message 생성된 메시지
     * @param finishReason 종료 사유 ({@code stop}, {@code length}, {@code error} 등)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    /**
     * 생성된 메시지.
     *
     * @param content 내용. response_format 이 json_schema 이면 JSON 문자열
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {
    }

    /**
     * 오류.
     *
     * @param code 오류 코드
     * @param message 오류 메시지
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Error(Integer code, String message) {
    }
}
