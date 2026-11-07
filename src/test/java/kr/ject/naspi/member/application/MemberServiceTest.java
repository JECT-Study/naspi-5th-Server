package kr.ject.naspi.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.member.domain.Member;
import kr.ject.naspi.member.domain.MemberName;
import kr.ject.naspi.member.domain.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@ActiveProfiles("test")
@DataJpaTest
@RecordApplicationEvents
@Import({MemberService.class, MemberAuthService.class, ClockConfig.class})
class MemberServiceTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("회원은 자신의 이름을 조회할 수 있다")
    void getName() {
        long memberId = join(1L, "홍길동");

        assertThat(memberService.getName(memberId)).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("존재하지 않는 회원의 이름은 조회할 수 없다")
    void getNameOfUnknownMember() {
        assertThatThrownBy(() -> memberService.getName(999L))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원은 최대 5자의 한글로 이름을 수정할 수 있다")
    void changeName() {
        long memberId = join(1L, "홍길동");

        String changed = memberService.changeName(memberId, "김철수");

        assertThat(changed).isEqualTo("김철수");
        assertThat(memberRepository.findById(memberId).orElseThrow().getName())
                .isEqualTo(MemberName.of("김철수"));
    }

    @Test
    @DisplayName("다른 회원과 같은 이름으로 수정할 수 있다 (중복 허용)")
    void changeNameToDuplicate() {
        join(1L, "홍길동");
        long memberId = join(2L, "김철수");

        assertThat(memberService.changeName(memberId, "홍길동")).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("한글이 아닌 문자가 있거나 5자를 넘는 이름으로는 수정할 수 없고 기존 이름이 유지된다")
    void changeNameWithInvalidName() {
        long memberId = join(1L, "홍길동");

        for (String invalid : new String[] {"John", "홍 길동", "홍길동1", "홍길동김철수", ""}) {
            assertThatThrownBy(() -> memberService.changeName(memberId, invalid))
                    .isInstanceOf(NaspiBizException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_MEMBER_NAME);
        }
        assertThat(memberRepository.findById(memberId).orElseThrow().getName())
                .isEqualTo(MemberName.of("홍길동"));
    }

    @Test
    @DisplayName("존재하지 않는 회원의 이름은 수정할 수 없다")
    void changeNameOfUnknownMember() {
        assertThatThrownBy(() -> memberService.changeName(999L, "김철수"))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴하면 회원 정보가 즉시 삭제(hard delete)되고 탈퇴 이벤트가 발행된다")
    void withdraw() {
        long memberId = join(1L, "홍길동");

        memberService.withdraw(memberId);

        assertThat(memberRepository.findById(memberId)).isEmpty();
        assertThat(events.stream(MemberWithdrawn.class))
                .containsExactly(new MemberWithdrawn(memberId));
    }

    @Test
    @DisplayName("탈퇴한 카카오 계정은 기존 회원으로 로그인되지 않고, 다시 가입하면 신규 회원이 된다")
    void loginAfterWithdraw() {
        long memberId = join(1L, "홍길동");
        memberService.withdraw(memberId);

        assertThat(memberAuthService.findMemberId(1L)).isEmpty();
        long rejoinedId = memberAuthService.joinWithKakaoName(1L, "홍길동").orElseThrow();
        assertThat(rejoinedId).isNotEqualTo(memberId);
    }

    @Test
    @DisplayName("존재하지 않는 회원은 탈퇴할 수 없고 탈퇴 이벤트도 발행되지 않는다")
    void withdrawUnknownMember() {
        assertThatThrownBy(() -> memberService.withdraw(999L))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        assertThat(events.stream(MemberWithdrawn.class)).isEmpty();
    }

    private long join(long kakaoId, String name) {
        return memberRepository.save(Member.join(kakaoId, MemberName.of(name),
                LocalDateTime.of(2026, 11, 4, 9, 0))).getId();
    }
}
