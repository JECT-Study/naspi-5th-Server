package kr.ject.naspi.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 카카오 로그인을 시작할 때 로그인 후 돌아갈 프론트 출처를 저장한다 ({@link LoginRedirect}).
 *
 * <p>인가 요청 생성은 기본 구현에 맡기고, 로그인 시작 요청일 때만 출처를 저장한다.
 */
class OriginSavingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final LoginRedirect loginRedirect;

    OriginSavingAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            LoginRedirect loginRedirect) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.loginRedirect = loginRedirect;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return saveOriginIfStarted(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request,
                                              String clientRegistrationId) {
        return saveOriginIfStarted(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest saveOriginIfStarted(
            HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest != null) {
            loginRedirect.saveOrigin(request);
        }
        return authorizationRequest;
    }
}
