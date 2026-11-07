package kr.ject.naspi.common.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Swagger UI·API 문서 접근 계정 설정 ({@code naspi.swagger}).
 *
 * <p>API 문서는 개발자만 보도록 HTTP Basic 로그인으로 막는다. 비밀번호는 환경 변수({@code SWAGGER_PASSWORD})로만
 * 주입하며, 값이 없으면 애플리케이션이 시작되지 않는다.
 *
 * @param username 접속 ID
 * @param password 접속 비밀번호
 */
@Validated
@ConfigurationProperties("naspi.swagger")
public record SwaggerAuthProperties(@NotBlank String username, @NotBlank String password) {

    @Override
    public String toString() {
        return "SwaggerAuthProperties[username=" + username + ", password=****]";
    }
}
