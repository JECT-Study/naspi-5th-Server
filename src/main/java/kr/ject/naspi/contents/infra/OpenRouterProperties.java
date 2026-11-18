package kr.ject.naspi.contents.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenRouter 호출 설정 ({@code naspi.ai.openrouter}).
 *
 * @param baseUrl API 주소
 * @param apiKey API 키. k8s Secret 으로 주입한다
 * @param model 모델 ID (예: {@code google/gemini-2.5-flash}). 이미지 입력과 structured outputs 를 지원해야 한다
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간
 * @param retryDelay 실패 후 다음 시도까지 기다리는 시간
 */
@ConfigurationProperties("naspi.ai.openrouter")
record OpenRouterProperties(
        @DefaultValue("https://openrouter.ai/api/v1") String baseUrl,
        String apiKey,
        String model,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout,
        @DefaultValue("1s") Duration retryDelay) {
}
