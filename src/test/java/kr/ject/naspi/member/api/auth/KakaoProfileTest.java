package kr.ject.naspi.member.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoProfileTest {

    @Test
    @DisplayName("카카오 사용자 정보에서 회원 번호와 닉네임을 꺼낸다")
    void from() {
        KakaoProfile profile = KakaoProfile.from(Map.of(
                "id", 12345L,
                "kakao_account", Map.of("profile", Map.of("nickname", "홍길동"))));

        assertThat(profile).isEqualTo(new KakaoProfile(12345L, "홍길동"));
    }

    @Test
    @DisplayName("닉네임 제공에 동의하지 않았으면 닉네임 없이 변환한다")
    void withoutNickname() {
        KakaoProfile profile = KakaoProfile.from(Map.of("id", 12345));

        assertThat(profile).isEqualTo(new KakaoProfile(12345L, null));
    }

    @Test
    @DisplayName("회원 번호가 없으면 변환할 수 없다")
    void withoutId() {
        assertThatThrownBy(() -> KakaoProfile.from(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
