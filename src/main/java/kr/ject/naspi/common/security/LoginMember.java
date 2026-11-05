package kr.ject.naspi.common.security;

import java.io.Serializable;

/**
 * 로그인한 회원. 세션(Redis)에 인증 정보로 저장된다.
 *
 * <p>세션 크기를 줄이기 위해 카카오 사용자 정보는 저장하지 않고 회원 ID만 둔다.
 * 컨트롤러에서는 {@code @AuthenticationPrincipal LoginMember}로 주입받는다.
 *
 * @param memberId 회원 ID
 */
public record LoginMember(long memberId) implements Serializable {
}
