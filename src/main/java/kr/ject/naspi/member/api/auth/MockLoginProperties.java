package kr.ject.naspi.member.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 모킹 로그인 설정 ({@code naspi.mock-login}).
 *
 * @param key 모킹 로그인 키. dev는 k8s Secret으로 주입한다. 비어 있으면 모든 요청을 거절한다
 */
@ConfigurationProperties("naspi.mock-login")
record MockLoginProperties(String key) {

    /**
     * 요청이 보낸 키가 맞는지 확인한다. 응답 시간으로 키를 추측할 수 없도록 상수 시간으로 비교한다.
     *
     * @param requestKey 요청이 보낸 키, 없으면 null
     * @return 키가 설정되어 있고 같으면 true
     */
    boolean matches(String requestKey) {
        if (!StringUtils.hasText(key) || requestKey == null) {
            return false;
        }
        return MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
                requestKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "MockLoginProperties[key=****]";
    }
}
