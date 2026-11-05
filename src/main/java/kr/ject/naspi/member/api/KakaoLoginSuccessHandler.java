package kr.ject.naspi.member.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import kr.ject.naspi.common.security.AuthProperties;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.member.application.MemberAuthService;
import kr.ject.naspi.member.infra.KakaoProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 카카오 인증을 마친 사용자를 로그인시킨다. 신규 사용자는 카카오 이름으로 자동 가입한다.
 *
 * <p>개인정보처리방침·서비스이용약관은 카카오 동의 화면에서 필수로 동의받으므로 인증을 마치면 동의한 것으로 본다.
 *
 * <ul>
 *   <li>기존 회원: 회원으로 로그인하고 로그인 완료 경로로 이동한다. 다시 가입되지 않는다.</li>
 *   <li>신규 회원: 카카오 이름으로 가입한 뒤 로그인하고 로그인 완료 경로로 이동한다.</li>
 *   <li>카카오 이름에 한글이 없는 신규 회원: 로그인시키지 않고 가입 대기 정보만 세션에 둔 뒤 이름 입력 경로로 이동한다.
 *       이름을 입력해 {@code POST /api/members/signup}으로 가입을 마친다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class KakaoLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberAuthService memberAuthService;
    private final LoginSession loginSession;
    private final SignupSession signupSession;
    private final AuthProperties authProperties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User kakaoUser = (OAuth2User) authentication.getPrincipal();
        KakaoProfile profile = KakaoProfile.from(kakaoUser.getAttributes());
        Optional<Long> memberId = memberAuthService.findMemberId(profile.kakaoId())
                .or(() -> memberAuthService.joinWithKakaoName(
                        profile.kakaoId(), profile.nickname()));

        // 카카오 인증 정보는 세션에 남기지 않는다. 회원이면 회원 ID만 담은 인증 정보로 바꾼다.
        loginSession.clear(request, response);
        signupSession.remove(request);
        if (memberId.isPresent()) {
            loginSession.login(request, response, memberId.get());
            response.sendRedirect(authProperties.redirect().loginSuccessUrl());
            return;
        }
        signupSession.save(request, new PendingSignup(profile.kakaoId()));
        response.sendRedirect(authProperties.redirect().nameInputUrl());
    }
}
