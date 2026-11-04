package kr.ject.naspi.member.api.response;

/**
 * 회원가입 결과.
 *
 * @param memberId 가입한 회원 ID
 */
public record SignupResponse(long memberId) {
}
