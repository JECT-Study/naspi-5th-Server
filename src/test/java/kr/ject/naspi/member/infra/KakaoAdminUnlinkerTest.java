package kr.ject.naspi.member.infra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class KakaoAdminUnlinkerTest {

    private static final String URL = "https://kapi.kakao.test/v1/user/unlink";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder()
                .baseUrl("https://kapi.kakao.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK admin-key");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    @DisplayName("어드민 키와 카카오 회원 번호로 연결 끊기 API를 호출한다")
    void unlink() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK admin-key"))
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "target_id_type", "user_id", "target_id", "12345")))
                .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));

        new KakaoAdminUnlinker(builder.build(), true).unlink(12345L);

        server.verify();
    }

    @Test
    @DisplayName("카카오가 오류를 응답하면 예외를 던진다")
    void unlinkFailure() {
        server.expect(once(), requestTo(URL)).andRespond(withBadRequest());

        KakaoAdminUnlinker unlinker = new KakaoAdminUnlinker(builder.build(), true);

        assertThatThrownBy(() -> unlinker.unlink(12345L)).isInstanceOf(RestClientException.class);
    }

    @Test
    @DisplayName("어드민 키가 없으면 카카오를 호출하지 않는다")
    void skipWithoutAdminKey() {
        server.expect(never(), requestTo(URL));

        new KakaoAdminUnlinker(builder.build(), false).unlink(12345L);

        server.verify();
    }
}
