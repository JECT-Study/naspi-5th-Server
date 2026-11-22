package kr.ject.naspi.member.infra;

import io.micrometer.observation.ObservationRegistry;
import java.net.http.HttpClient;
import kr.ject.naspi.member.domain.KakaoAccountUnlinker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 앱 어드민 키로 카카오 연결 끊기 API({@code POST /v1/user/unlink})를 호출한다.
 *
 * <p>로그인 후 카카오 액세스 토큰을 저장하지 않으므로, 사용자 토큰 대신 어드민 키와 카카오 회원 번호로 호출한다.
 * 어드민 키는 카카오 개발자 콘솔의 호출 허용 IP에 등록된 서버에서만 쓸 수 있다.
 * 어드민 키가 없으면(로컬·테스트) 호출하지 않는다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(KakaoAdminProperties.class)
class KakaoAdminUnlinker implements KakaoAccountUnlinker {

    private final RestClient restClient;
    private final boolean enabled;

    @Autowired
    KakaoAdminUnlinker(KakaoAdminProperties properties, ObservationRegistry observationRegistry) {
        this(restClient(properties, observationRegistry),
                StringUtils.hasText(properties.adminKey()));
    }

    KakaoAdminUnlinker(RestClient restClient, boolean enabled) {
        this.restClient = restClient;
        this.enabled = enabled;
    }

    private static RestClient restClient(KakaoAdminProperties properties,
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
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
                .build();
    }

    @Override
    public void unlink(long kakaoId) {
        if (!enabled) {
            log.warn("카카오 어드민 키가 없어 연결 끊기를 건너뜀: kakaoId={}", kakaoId);
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", Long.toString(kakaoId));
        restClient.post()
                .uri("/v1/user/unlink")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
