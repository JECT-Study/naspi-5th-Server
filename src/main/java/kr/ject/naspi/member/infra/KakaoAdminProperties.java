package kr.ject.naspi.member.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카카오 어드민 키 API 호출 설정 ({@code naspi.kakao}).
 *
 * @param baseUrl API 주소
 * @param adminKey 앱 어드민 키. k8s Secret 으로 주입한다. 비어 있으면 호출하지 않는다 (로컬·테스트)
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간
 */
@ConfigurationProperties("naspi.kakao")
record KakaoAdminProperties(
        @DefaultValue("https://kapi.kakao.com") String baseUrl,
        String adminKey,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {
}
