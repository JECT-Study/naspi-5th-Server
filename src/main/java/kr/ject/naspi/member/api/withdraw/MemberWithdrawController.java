package kr.ject.naspi.member.api.withdraw;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.common.security.LoginSession;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴.
 *
 * <p>정책 (docs/policies/v1/member.md "회원 탈퇴"): 즉시 탈퇴 처리하고 회원 정보를 hard delete 한다.
 */
@Tag(name = "회원 탈퇴", description = "즉시 탈퇴")
@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberWithdrawController {

    private final MemberService memberService;
    private final LoginSession loginSession;

    /**
     * 로그인한 회원을 즉시 탈퇴시키고 로그아웃한다.
     *
     * @param loginMember 로그인한 회원
     * @param request 요청
     * @return 성공 응답
     * @throws NaspiBizException 회원 정보가 없는 경우
     */
    @Operation(
            summary = "회원 탈퇴",
            description = """
                    로그인한 회원을 즉시 탈퇴시킨다. 확인 절차 없이 바로 처리되며 되돌릴 수 없다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`. 회원 정보가 삭제되고 로그아웃된다.
                    같은 카카오 계정으로 다시 로그인하면 신규 회원으로 가입된다.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"103"` | 회원 정보 없음 (이미 탈퇴) | 카카오 로그인부터 다시 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PostMapping("/withdraw")
    public Result<Void> withdraw(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            HttpServletRequest request) {
        memberService.withdraw(loginMember.memberId());
        loginSession.logout(request);
        return Result.success();
    }
}
