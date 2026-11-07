package kr.ject.naspi.member.api.auth;

import java.util.Map;

/**
 * 카카오 사용자 정보 API({@code /v2/user/me}) 응답에서 필요한 값.
 *
 * <p>응답 형식: {@code {"id": 123, "kakao_account": {"profile": {"nickname": "홍길동"}}}}.
 * 닉네임은 사용자가 동의하지 않으면 없을 수 있다.
 *
 * @param kakaoId 카카오 회원 번호
 * @param nickname 카카오 닉네임, 없으면 null
 */
record KakaoProfile(long kakaoId, String nickname) {

    /**
     * 카카오 사용자 정보 응답을 변환한다.
     *
     * @param attributes 카카오 사용자 정보 응답
     * @return 카카오 사용자 정보
     * @throws IllegalArgumentException 카카오 회원 번호가 없는 경우
     */
    public static KakaoProfile from(Map<String, Object> attributes) {
        if (!(attributes.get("id") instanceof Number id)) {
            throw new IllegalArgumentException("카카오 사용자 정보에 회원 번호가 없습니다.");
        }
        return new KakaoProfile(id.longValue(), nickname(attributes));
    }

    private static String nickname(Map<String, Object> attributes) {
        if (attributes.get("kakao_account") instanceof Map<?, ?> account
                && account.get("profile") instanceof Map<?, ?> profile
                && profile.get("nickname") instanceof String nickname) {
            return nickname;
        }
        return null;
    }
}
