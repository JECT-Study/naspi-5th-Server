package kr.ject.naspi.member.api;

import jakarta.servlet.http.HttpServletRequest;
import kr.ject.naspi.global.exception.BusinessException;
import kr.ject.naspi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link PendingSignupSession}이 붙은 {@link PendingSignup} 파라미터에 세션의 가입 대기 정보를 넣는다.
 *
 * <p>컨트롤러 호출 직전에 실행되므로, 여기서 던진 예외도 공통 예외 처리(GlobalExceptionHandler)를 거친다.
 */
@RequiredArgsConstructor
public class PendingSignupArgumentResolver implements HandlerMethodArgumentResolver {

    private final SignupSession signupSession;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(PendingSignupSession.class)
                && PendingSignup.class.equals(parameter.getParameterType());
    }

    @Override
    public PendingSignup resolveArgument(MethodParameter parameter,
                                         ModelAndViewContainer mavContainer,
                                         NativeWebRequest webRequest,
                                         WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return signupSession.find(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "카카오 로그인 후 가입할 수 있습니다."));
    }
}
