package kr.ject.naspi.member.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그아웃.
 *
 * <p>정책 (docs/policies/v1/member.md "로그인/ 회원가입"): 서비스 로그인 상태만 종료하며 카카오 계정 로그아웃은 하지 않는다.
 */
@Tag(name = "인증", description = "카카오 로그인과 회원가입")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LogoutController {

    private final LoginSession loginSession;

    /**
     * 로그인 세션을 끝낸다. 로그인하지 않은 상태에서 호출해도 성공으로 응답한다.
     *
     * @param request 요청
     * @return 성공 응답
     */
    @Operation(
            summary = "로그아웃",
            description = """
                    서비스 로그인 상태를 끝낸다. 카카오 계정은 로그아웃하지 않는다.

                    **요청**
                    - 세션 쿠키를 함께 보낸다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`. 세션이 삭제되고 세션 쿠키가 만료된다.
                    로그인하지 않았거나 세션이 만료된 상태에서 호출해도 같은 성공 응답을 받는다.
                    """)
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        loginSession.logout(request);
        return Result.success();
    }
}
