package kr.ject.naspi.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.BusinessException;
import kr.ject.naspi.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Test
    @DisplayName("약관에 동의하면 가입되고 탈퇴하지 않은 상태로 시작한다")
    void join() {
        Member member = Member.join(12345L, MemberName.of("홍길동"), true, NOW);

        assertThat(member.getKakaoId()).isEqualTo(12345L);
        assertThat(member.getName()).isEqualTo(MemberName.of("홍길동"));
        assertThat(member.isTermsAgreed()).isTrue();
        assertThat(member.isWithdrawn()).isFalse();
        assertThat(member.getJoinedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("약관에 동의하지 않으면 가입할 수 없다")
    void joinWithoutTerms() {
        assertThatThrownBy(() -> Member.join(12345L, MemberName.of("홍길동"), false, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("이름을 변경할 수 있다")
    void changeName() {
        Member member = Member.join(12345L, MemberName.of("홍길동"), true, NOW);

        member.changeName(MemberName.of("김철수"));

        assertThat(member.getName()).isEqualTo(MemberName.of("김철수"));
    }

    @Test
    @DisplayName("탈퇴하면 탈퇴 상태가 된다")
    void withdraw() {
        Member member = Member.join(12345L, MemberName.of("홍길동"), true, NOW);

        member.withdraw();

        assertThat(member.isWithdrawn()).isTrue();
    }
}
