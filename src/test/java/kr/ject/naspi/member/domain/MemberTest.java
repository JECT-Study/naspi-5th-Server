package kr.ject.naspi.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Test
    @DisplayName("약관은 카카오 동의 화면에서 받으므로 약관 동의 상태로 가입되고 탈퇴하지 않은 상태로 시작한다")
    void join() {
        Member member = Member.join(12345L, MemberName.of("홍길동"), NOW);

        assertThat(member.getKakaoId()).isEqualTo(12345L);
        assertThat(member.getName()).isEqualTo(MemberName.of("홍길동"));
        assertThat(member.isTermsAgreed()).isTrue();
        assertThat(member.isWithdrawn()).isFalse();
        assertThat(member.getJoinedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이름을 변경할 수 있다")
    void changeName() {
        Member member = Member.join(12345L, MemberName.of("홍길동"), NOW);

        member.changeName(MemberName.of("김철수"));

        assertThat(member.getName()).isEqualTo(MemberName.of("김철수"));
    }

    @Test
    @DisplayName("탈퇴하면 탈퇴 상태가 된다")
    void withdraw() {
        Member member = Member.join(12345L, MemberName.of("홍길동"), NOW);

        member.withdraw();

        assertThat(member.isWithdrawn()).isTrue();
    }
}
