package kr.ject.naspi.member.api.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 세션의 가입 대기 정보({@link PendingSignup})를 주입받는다.
 *
 * <p>가입 대기 정보가 없으면(카카오 인증 전이거나 세션 만료) 인증 필요 오류로 응답한다.
 *
 * <pre>{@code
 * public Result<?> signup(@PendingSignupSession PendingSignup pending) { ... }
 * }</pre>
 *
 * @see PendingSignupArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface PendingSignupSession {
}
