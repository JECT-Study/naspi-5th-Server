package kr.ject.naspi.member.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StringUtils;

class MockLoginProfileTest {

    @ParameterizedTest
    @CsvSource({
        "local, true",
        "development, true",
        "test, true",
        "production, false",
        "'development,production', false",
        "staging, false",
        "'', false",
    })
    @DisplayName("모킹 로그인은 local·development·test 프로필에서만 등록되고, production이거나 프로필이 없으면 등록되지 않는다")
    void registeredOnlyInListedProfiles(String profiles, boolean registered) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(StringUtils.commaDelimitedListToStringArray(profiles));
        // application.yaml의 spring.profiles.default와 같다
        environment.setDefaultProfiles("production");

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(MockLoginController.class);

            assertThat(context.containsBeanDefinition("mockLoginController"))
                    .isEqualTo(registered);
        }
    }
}
