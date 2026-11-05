package kr.ject.naspi.member.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.ject.naspi.common.response.CommonResponse;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.global.exception.BusinessException;
import kr.ject.naspi.member.api.request.SignupRequest;
import kr.ject.naspi.member.api.response.SignupResponse;
import kr.ject.naspi.member.application.MemberAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 이름에 한글이 없어 자동 가입되지 않은 사용자의 회원가입.
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberSignupController {

    private final MemberAuthService memberAuthService;
    private final LoginSession loginSession;
    private final SignupSession signupSession;

    /**
     * 입력한 이름으로 회원가입을 마치고 로그인 상태로 만든다.
     *
     * @param request 회원가입 요청
     * @param pending 카카오 인증 후 세션에 저장된 가입 대기 정보 (없으면 인증 필요 오류)
     * @param httpRequest 요청
     * @param httpResponse 응답
     * @return 가입한 회원 ID
     * @throws BusinessException 이미 가입했거나 이름이 규칙에 맞지 않는 경우
     */
    @PostMapping("/signup")
    public CommonResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request,
                                                 @PendingSignupSession PendingSignup pending,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        long memberId = memberAuthService.joinWithInputName(pending.kakaoId(), request.name());

        signupSession.remove(httpRequest);
        loginSession.login(httpRequest, httpResponse, memberId);
        return CommonResponse.success(new SignupResponse(memberId));
    }
}
