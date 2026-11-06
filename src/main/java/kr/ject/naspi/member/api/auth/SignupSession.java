package kr.ject.naspi.member.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;

/**
 * 가입 대기 정보({@link PendingSignup})를 세션에 저장하고 꺼낸다.
 *
 * <p>세션 키, 형변환, 세션이 없는 경우의 처리를 이곳에 모은다.
 * 세션은 Spring Session을 통해 Redis에 저장되며, 로그인 전 세션 유지 시간({@code spring.session.timeout})만 유지된다.
 * 빈 등록은 {@link AuthWebConfig}에서 한다.
 */
class SignupSession {

    /** 세션 속성 이름. */
    static final String ATTRIBUTE = PendingSignup.class.getName();

    /**
     * 가입 대기 정보를 저장한다. 세션이 없으면 만든다.
     *
     * @param request 요청
     * @param pending 가입 대기 정보
     */
    public void save(HttpServletRequest request, PendingSignup pending) {
        request.getSession().setAttribute(ATTRIBUTE, pending);
    }

    /**
     * 가입 대기 정보를 꺼낸다. 세션이 없어도 새로 만들지 않는다.
     *
     * @param request 요청
     * @return 가입 대기 정보, 없으면 빈 값
     */
    public Optional<PendingSignup> find(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(ATTRIBUTE) instanceof PendingSignup pending) {
            return Optional.of(pending);
        }
        return Optional.empty();
    }

    /**
     * 가입 대기 정보를 지운다.
     *
     * @param request 요청
     */
    public void remove(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }
}
