package kr.ject.naspi.member.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.application.MemberAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 인증 없이 테스트 회원으로 로그인한다 (E2E 테스트 전용).
 *
 * <p>로그인된 세션은 카카오 로그인과 같다. 운영에서는 컨트롤러를 등록하지 않아 404가 된다.
 * 실수로 열리지 않도록 "운영이 아니면 허용"이 아니라 허용할 프로필을 나열한다. 프로필을 지정하지 않으면
 * production으로 동작하므로({@code spring.profiles.default}) 막히고, production이 함께 지정된 경우도 막힌다.
 *
 * <p>테스트 회원은 음수 카카오 회원 번호로 가입하므로 실제 카카오 회원으로는 로그인할 수 없다.
 */
@Tag(name = "인증", description = "카카오 로그인과 회원가입")
@RestController
@RequestMapping("/api/auth")
@Profile("(local | development | test) & !production")
@RequiredArgsConstructor
public class MockLoginController {

    private final MemberAuthService memberAuthService;
    private final LoginSession loginSession;
    private final SignupSession signupSession;

    /**
     * 테스트 회원으로 로그인한다. 처음 쓰는 번호면 가입한 뒤 로그인한다.
     *
     * @param request 모킹 로그인 요청
     * @param httpRequest 요청
     * @param httpResponse 응답
     * @return 로그인한 회원 ID
     * @throws NaspiBizException 처음 쓰는 번호인데 이름이 규칙에 맞지 않는 경우
     */
    @Operation(
            summary = "모킹 로그인 (테스트 전용)",
            description = """
                    카카오 인증 없이 테스트 회원으로 로그인한다. 운영(production)에는 없는 API다 (404).

                    **요청**
                    - `userNo`: 테스트 회원 번호 1~1000. 같은 번호는 항상 같은 회원이다. \
                    여러 명이 필요하면 번호를 다르게 보낸다.
                    - `name`: 처음 쓰는 번호로 가입할 때의 이름 (1~5자 한글, 생략하면 `테스터`). \
                    이미 가입한 회원이면 무시한다.

                    **성공 시** HTTP 200, `code` `"000"`. 세션 쿠키가 발급되고 카카오 로그인과 같은 상태가 된다 \
                    (로그인 유지 7일). 로그아웃·탈퇴한 뒤에도 같은 번호로 다시 로그인할 수 있다 \
                    (탈퇴했으면 새 회원으로 가입된다).

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 |
                    |---|---|---|
                    | 200 | `"101"` | 처음 쓰는 번호인데 이름이 규칙에 맞지 않음 |
                    | 400 | `"002"` | `userNo` 누락 또는 범위(1~1000) 밖 |
                    """)
    @PostMapping("/mock-login")
    public Result<MockLoginResponse> login(@Valid @RequestBody MockLoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        long kakaoId = request.mockKakaoId();
        long memberId = memberAuthService.findMemberId(kakaoId).orElseGet(() ->
                memberAuthService.joinWithInputName(kakaoId, request.nameOrDefault()));

        signupSession.remove(httpRequest);
        loginSession.login(httpRequest, httpResponse, memberId);
        return Result.success(new MockLoginResponse(memberId));
    }
}
