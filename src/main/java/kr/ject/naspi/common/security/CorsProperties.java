package kr.ject.naspi.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 설정 ({@code naspi.cors}).
 *
 * <p>프론트({@code naspi.my})와 API({@code api.naspi.my})는 출처가 달라 브라우저가 CORS 허용을 확인한다.
 * 세션 쿠키를 함께 보내야 하므로 허용 출처를 와일드카드 없이 정확히 지정한다.
 *
 * @param allowedOrigins API 호출을 허용할 프론트 출처 (예: {@code https://naspi.my})
 */
@ConfigurationProperties("naspi.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
