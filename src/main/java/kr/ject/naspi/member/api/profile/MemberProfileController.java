package kr.ject.naspi.member.api.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ject.naspi.common.response.Result;
import kr.ject.naspi.common.security.LoginMember;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원 자신의 정보.
 */
@Tag(name = "회원 정보", description = "로그인한 회원 자신의 정보 조회·수정")
@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberProfileController {

    private final MemberService memberService;

    /**
     * 로그인한 회원의 이름을 조회한다.
     *
     * @param loginMember 로그인한 회원
     * @return 이름
     * @throws NaspiBizException 회원 정보가 없는 경우
     */
    @Operation(
            summary = "내 이름 조회",
            description = """
                    로그인한 회원의 이름을 조회한다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.

                    **성공 시** HTTP 200, `code` `"000"`, `data.name`에 이름.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"103"` | 회원 정보 없음 (탈퇴 등) | 카카오 로그인부터 다시 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @GetMapping("/name")
    public Result<MemberNameResponse> getName(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember) {
        String name = memberService.getName(loginMember.memberId());
        return Result.success(new MemberNameResponse(name));
    }

    /**
     * 로그인한 회원의 이름을 수정한다.
     *
     * @param loginMember 로그인한 회원
     * @param request 이름 수정 요청
     * @return 수정된 이름
     * @throws NaspiBizException 회원 정보가 없거나, 이름이 규칙(최대 5자 한글)에 맞지 않는 경우
     */
    @Operation(
            summary = "내 이름 수정",
            description = """
                    로그인한 회원의 이름을 수정한다.

                    **요청**
                    - 로그인 세션 쿠키가 필요하다. \
                    `credentials: 'include'`(axios: `withCredentials: true`)로 호출한다.
                    - 이름: 1~5자 한글. 영어·숫자·특수문자·공백 불가. 다른 회원과 같은 이름도 가능.

                    **성공 시** HTTP 200, `code` `"000"`, `data.name`에 수정된 이름.

                    **실패 응답** (`code`는 3자리 문자열)

                    | HTTP | code | 상황 | 프론트 처리 |
                    |---|---|---|---|
                    | 200 | `"101"` | 이름이 규칙에 맞지 않음 | 메시지 표시 후 다시 입력 |
                    | 200 | `"103"` | 회원 정보 없음 (탈퇴 등) | 카카오 로그인부터 다시 |
                    | 400 | `"002"` | `name` 누락·공백 | 다시 입력 |
                    | 401 | `"003"` | 로그인하지 않음 (세션 만료) | 카카오 로그인부터 다시 |
                    """)
    @PatchMapping("/name")
    public Result<MemberNameResponse> changeName(
            @Parameter(hidden = true) @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody ChangeNameRequest request) {
        String name = memberService.changeName(loginMember.memberId(), request.name());
        return Result.success(new MemberNameResponse(name));
    }
}
