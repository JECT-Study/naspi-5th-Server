package kr.ject.naspi.contents.infra;

import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Function;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenRouter chat completions 를 호출한다.
 *
 * <p>재시도 (docs/ai-news/README.md "2.3"): 최초 요청 포함 최대 3회 시도한다. 호출 오류(타임아웃, 4xx·5xx),
 * 응답 처리 오류(빈 응답, 공급자 오류), {@code contentHandler} 가 던진 예외(파싱·출력 검증 실패)를 모두 같은 한도로
 * 재시도한다. {@code contentHandler} 가 정상 반환한 값(미생성 판단 포함)은 재시도하지 않는다.
 */
@Component
@EnableConfigurationProperties(OpenRouterProperties.class)
class OpenRouterClient {

    /** 최초 요청을 제외한 재시도 횟수. */
    static final int MAX_RETRIES = 2;

    private final RestClient restClient;
    private final RetryTemplate retryTemplate;

    @Autowired
    OpenRouterClient(OpenRouterProperties properties, ObservationRegistry observationRegistry) {
        this(restClient(properties, observationRegistry), properties.retryDelay());
    }

    OpenRouterClient(RestClient restClient, Duration retryDelay) {
        this.restClient = restClient;
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(MAX_RETRIES)
                .delay(retryDelay)
                .build());
    }

    private static RestClient restClient(OpenRouterProperties properties,
                                         ObservationRegistry observationRegistry) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                // 호출 시간·상태를 메트릭과 트레이스에 남긴다 (http.client.requests)
                .observationRegistry(observationRegistry)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }

    /**
     * 요청을 보내고, 생성된 메시지 내용을 {@code contentHandler} 로 변환해 반환한다. 실패하면 재시도한다.
     *
     * @param request 요청 본문
     * @param contentHandler 메시지 내용(문자열)을 결과로 변환한다. 내용이 잘못됐으면 예외를 던져 재시도하게 한다
     * @param <T> 결과 타입
     * @return 변환 결과
     * @throws NaspiRuntimeException 모든 시도가 실패한 경우. 원인은 마지막 실패다
     */
    <T> T complete(OpenRouterRequest request, Function<String, T> contentHandler) {
        try {
            return retryTemplate.execute(() -> contentHandler.apply(call(request)));
        } catch (RetryException e) {
            throw new NaspiRuntimeException(
                    "OpenRouter 호출 %d회 모두 실패: %s".formatted(
                            e.getRetryCount() + 1, e.getLastException().getMessage()),
                    e.getLastException());
        }
    }

    private String call(OpenRouterRequest request) {
        OpenRouterResponse response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OpenRouterResponse.class);
        if (response == null) {
            throw new IllegalStateException("OpenRouter 응답 본문이 없습니다");
        }
        if (response.error() != null) {
            throw new IllegalStateException("OpenRouter 오류 응답: %d %s".formatted(
                    response.error().code(), response.error().message()));
        }
        if (response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenRouter 응답에 choices 가 없습니다");
        }
        OpenRouterResponse.Message message = response.choices().getFirst().message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new IllegalStateException("OpenRouter 응답 내용이 비어 있습니다");
        }
        return message.content();
    }
}
