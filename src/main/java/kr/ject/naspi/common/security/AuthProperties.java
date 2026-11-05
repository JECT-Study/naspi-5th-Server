package kr.ject.naspi.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 관련 설정 ({@code naspi.auth}).
 *
 * @param loginSessionTimeout 로그인 유지 기간, 마지막 요청으로부터 이 기간 동안 요청이 없으면 로그아웃
 * @param redirect 카카오 로그인 후 이동할 프론트 경로
 */
@ConfigurationProperties("naspi.auth")
public record AuthProperties(Duration loginSessionTimeout, Redirect redirect) {

    /**
     * 카카오 로그인 결과별로 이동할 프론트 경로.
     *
     * @param loginSuccessUrl 기존 회원 로그인 완료
     * @param nameInputUrl 이름 입력 화면 (카카오 이름에 한글이 없는 신규 회원)
     * @param loginFailureUrl 카카오 인증 실패 또는 사용자 취소
     */
    public record Redirect(String loginSuccessUrl, String nameInputUrl, String loginFailureUrl) {
    }
}
