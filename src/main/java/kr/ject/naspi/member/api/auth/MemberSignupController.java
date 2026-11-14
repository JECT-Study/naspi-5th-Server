package kr.ject.naspi.member.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.application.MemberAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 이름에 한글이 없어 자동 가입되지 않은 사용자의 회원가입.
 */
@Tag(name = "인증", description = "카카오 로그인과 회원가입")
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
     * @throws NaspiBizException 이미 가입했거나 이름이 규칙에 맞지 않는 경우
     */
    @Operation(
            summary = "이름 입력 회원가입",
            description = """
                    카카오 이름에 한글이 없어 자동 가입되지 않은 사용자가 이름을 입력해 가입을 마친다.

                    **언제 호출하나**
                    - 카카오 로그인 후 이름 입력 화면(`name-input-url`, 예: `/signup`)으로 \
                    리다이렉트된 경우에만 호출한다.
                    - 기존 회원, 카카오 이름에 한글이 있는 신규 회원은 카카오 로그인 중에 \
                    자동으로 로그인·가입되므로 호출하지 않는다.

                    **요청**
                    - 카카오 로그인에서 받은 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 이름: 1~5자 한글 (자음·모음만 있는 글자도 한 글자). 영어·숫자·특수문자·공백 불가.

                    **성공 시** HTTP 200, `code` `"000"`. 가입한 회원으로 로그인된다 (로그인 유지 7일).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"100"` | 가입 대기 정보 없음 (카카오 로그인 안 함, 세션 만료) \
                    | 카카오 로그인부터 다시 |
                    | 200 | `"101"` | 이름이 규칙에 맞지 않음 | 메시지 표시 후 다시 입력 \
                    (가입 대기 상태 유지) |
                    | 200 | `"102"` | 이미 가입한 카카오 계정 | 카카오 로그인부터 다시 |
                    | 400 | `"002"` | `name` 누락·공백 | 다시 입력 |
                    """)
    @PostMapping("/signup")
    public Result<SignupResponse> signup(@Valid @RequestBody SignupRequest request,
                                                 @Parameter(hidden = true) @PendingSignupSession
                                                 PendingSignup pending,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        long memberId = memberAuthService.joinWithInputName(pending.kakaoId(), request.name());

        signupSession.remove(httpRequest);
        loginSession.login(httpRequest, httpResponse, memberId);
        return Result.success(new SignupResponse(memberId));
    }
}
