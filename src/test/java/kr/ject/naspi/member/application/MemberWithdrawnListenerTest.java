package kr.ject.naspi.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberWithdrawnListenerTest {

    @Test
    @DisplayName("회원 탈퇴 시 탈퇴한 회원의 카카오 계정 연결을 끊는다")
    void unlinkKakaoAccount() {
        List<Long> unlinked = new ArrayList<>();
        MemberWithdrawnListener listener = new MemberWithdrawnListener(unlinked::add);

        listener.onMemberWithdrawn(new MemberWithdrawn(1L, 12345L));

        assertThat(unlinked).containsExactly(12345L);
    }

    @Test
    @DisplayName("카카오 연결 끊기에 실패해도 예외를 던지지 않는다 (탈퇴는 처리)")
    void ignoreUnlinkFailure() {
        MemberWithdrawnListener listener = new MemberWithdrawnListener(kakaoId -> {
            throw new IllegalStateException("카카오 오류");
        });

        assertThatCode(() -> listener.onMemberWithdrawn(new MemberWithdrawn(1L, 12345L)))
                .doesNotThrowAnyException();
    }
}
