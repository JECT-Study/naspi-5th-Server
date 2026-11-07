package kr.ject.naspi.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class SwaggerAuthPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "naspi.swagger.username=${SWAGGER_USERNAME:nspusr}",
                    "naspi.swagger.password=${SWAGGER_PASSWORD:}");

    @Test
    @DisplayName("Swagger 비밀번호 환경 변수가 없으면 애플리케이션이 시작되지 않는다")
    void failsWithoutPassword() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Swagger 비밀번호 환경 변수가 있으면 그 값을 쓴다")
    void bindsPassword() {
        runner.withSystemProperties("SWAGGER_PASSWORD=secret")
                .run(context -> {
                    SwaggerAuthProperties properties = context.getBean(SwaggerAuthProperties.class);
                    assertThat(properties.username()).isEqualTo("nspusr");
                    assertThat(properties.password()).isEqualTo("secret");
                });
    }

    @Configuration
    @EnableConfigurationProperties(SwaggerAuthProperties.class)
    static class Config {
    }
}
