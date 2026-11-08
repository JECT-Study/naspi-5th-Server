package kr.ject.naspi.member.api.auth;

import java.io.Serializable;

/**
 * 카카오 인증은 마쳤지만 카카오 이름에 한글이 없어 이름 입력을 기다리는 사용자.
 * 가입을 마칠 때까지 세션에 저장한다 ({@link SignupSession}).
 *
 * @param kakaoId 카카오 회원 번호
 */
public record PendingSignup(long kakaoId) implements Serializable {
}
