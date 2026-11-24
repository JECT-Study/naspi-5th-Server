package kr.ject.naspi.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

class MockLoginBlockFilterTest {

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
    @DisplayName("local·development·test 프로필에서만 통과시키고, production이거나 프로필이 없으면 404로 끝낸다")
    void allowOnlyListedProfiles(String profiles, boolean allowed) throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(StringUtils.commaDelimitedListToStringArray(profiles));
        // application.yaml의 spring.profiles.default와 같다
        environment.setDefaultProfiles("production");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new MockLoginBlockFilter(environment, JsonMapper.builder().build()).doFilter(
                new MockHttpServletRequest("POST", MockLoginBlockFilter.PATH), response, chain);

        assertThat(chain.getRequest() != null).isEqualTo(allowed);
        assertThat(response.getStatus()).isEqualTo(allowed ? 200 : 404);
    }
}
