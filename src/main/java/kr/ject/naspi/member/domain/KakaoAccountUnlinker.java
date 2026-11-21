package kr.ject.naspi.member.domain;

/**
 * 서비스와 카카오 계정의 연결을 끊는다.
 *
 * <p>연결을 끊으면 카카오의 동의 내역이 지워져, 다시 로그인할 때 동의 화면(약관 동의 포함)을 다시 거친다.
 * 구현은 infra의 카카오 API 연동이 맡는다.
 */
public interface KakaoAccountUnlinker {

    /**
     * 카카오 계정의 연결을 끊는다.
     *
     * @param kakaoId 카카오 회원 번호
     * @throws RuntimeException 카카오 호출이 실패한 경우
     */
    void unlink(long kakaoId);
}
