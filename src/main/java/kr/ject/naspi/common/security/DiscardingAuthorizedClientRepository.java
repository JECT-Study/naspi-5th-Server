package kr.ject.naspi.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 카카오 access/refresh token을 저장하지 않는다.
 *
 * <p>카카오 토큰은 로그인 시 사용자 정보를 한 번 조회하는 데만 쓴다.
 * 기본 구현은 토큰을 서버 메모리에 보관해 서버 무상태 원칙과 맞지 않으므로 저장을 생략한다.
 */
public class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient,
                                     Authentication principal, HttpServletRequest request,
                                     HttpServletResponse response) {
        // 저장하지 않는다.
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        // 저장한 것이 없다.
    }
}
