package kr.ject.naspi.member.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ject.naspi.common.security.AuthProperties;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.member.application.MemberAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class KakaoLoginSuccessHandlerTest {

    private static final long KAKAO_ID = 12345L;
    private static final AuthProperties PROPERTIES = new AuthProperties(Duration.ofDays(7),
            new AuthProperties.Redirect("/home", "/name", "/fail"));

    private final MemberAuthService memberAuthService = mock(MemberAuthService.class);
    private final KakaoLoginSuccessHandler handler = new KakaoLoginSuccessHandler(
            memberAuthService,
            new LoginSession(new HttpSessionSecurityContextRepository(), PROPERTIES),
            new SignupSession(),
            PROPERTIES);

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("기존 회원이 다시 로그인하면 새로 가입되지 않고 회원으로 7일간 로그인된다")
    void existingMember() throws Exception {
        given(memberAuthService.findMemberId(KAKAO_ID)).willReturn(Optional.of(7L));

        handler.onAuthenticationSuccess(request, response, kakaoAuthentication("홍길동"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/home");
        assertThat(savedContext().getAuthentication().getPrincipal())
                .isEqualTo(new LoginMember(7L));
        assertThat(session().getAttribute(SignupSession.ATTRIBUTE)).isNull();
        assertThat(session().getMaxInactiveInterval()).isEqualTo(7 * 24 * 60 * 60);
        verify(memberAuthService, never()).joinWithKakaoName(anyLong(), any());
    }

    @Test
    @DisplayName("신규 회원은 카카오 동의 화면에서 약관에 동의했으므로 카카오 이름으로 자동 가입되고 7일간 로그인된다")
    void newMember() throws Exception {
        given(memberAuthService.findMemberId(KAKAO_ID)).willReturn(Optional.empty());
        given(memberAuthService.joinWithKakaoName(KAKAO_ID, "홍길동")).willReturn(Optional.of(8L));

        handler.onAuthenticationSuccess(request, response, kakaoAuthentication("홍길동"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/home");
        assertThat(savedContext().getAuthentication().getPrincipal())
                .isEqualTo(new LoginMember(8L));
        assertThat(session().getAttribute(SignupSession.ATTRIBUTE)).isNull();
        assertThat(session().getMaxInactiveInterval()).isEqualTo(7 * 24 * 60 * 60);
    }

    @Test
    @DisplayName("카카오 이름에 한글이 없는 신규 회원은 로그인되지 않고 가입 대기 상태로 이름 입력 화면으로 이동한다")
    void newMemberWithoutHangulName() throws Exception {
        given(memberAuthService.findMemberId(KAKAO_ID)).willReturn(Optional.empty());
        given(memberAuthService.joinWithKakaoName(KAKAO_ID, "John")).willReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, kakaoAuthentication("John"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/name");
        assertThat(savedContext()).isNull();
        assertThat(session().getAttribute(SignupSession.ATTRIBUTE))
                .isEqualTo(new PendingSignup(KAKAO_ID));
    }

    private OAuth2AuthenticationToken kakaoAuthentication(String nickname) {
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("id", KAKAO_ID,
                        "kakao_account", Map.of("profile", Map.of("nickname", nickname))),
                "id");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "kakao");
    }

    private MockHttpSession session() {
        return (MockHttpSession) request.getSession(false);
    }

    private SecurityContext savedContext() {
        SecurityContext context = (SecurityContext) session().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return context == null || context.getAuthentication() == null ? null : context;
    }
}
