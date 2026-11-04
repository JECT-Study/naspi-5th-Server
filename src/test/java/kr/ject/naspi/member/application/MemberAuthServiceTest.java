package kr.ject.naspi.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import kr.ject.naspi.global.config.ClockConfig;
import kr.ject.naspi.global.exception.BusinessException;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.member.domain.Member;
import kr.ject.naspi.member.domain.MemberName;
import kr.ject.naspi.member.infra.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@Import({MemberAuthService.class, MemberAuthServiceTest.FixedClock.class})
class MemberAuthServiceTest {

    private static final long KAKAO_ID = 12345L;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("카카오 이름에 한글이 있으면 서비스 이름 규칙에 맞게 변환한 이름으로 자동 가입되고 가입 일시가 기록된다")
    void joinWithKakaoName() {
        long memberId = memberAuthService.joinWithKakaoName(KAKAO_ID, "홍길동Kim123456").orElseThrow();

        Member member = memberRepository.findById(memberId).orElseThrow();
        assertThat(member.getKakaoId()).isEqualTo(KAKAO_ID);
        assertThat(member.getName()).isEqualTo(MemberName.of("홍길동"));
        assertThat(member.isTermsAgreed()).isTrue();
        assertThat(member.getJoinedAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 9, 0));
    }

    @Test
    @DisplayName("카카오 이름에 한글이 없으면 자동 가입되지 않고 이름 입력이 필요하다")
    void joinWithKakaoNameWithoutHangul() {
        assertThat(memberAuthService.joinWithKakaoName(KAKAO_ID, "John")).isEmpty();
        assertThat(memberAuthService.joinWithKakaoName(KAKAO_ID, null)).isEmpty();
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("카카오 이름에 한글이 없는 사용자는 입력한 이름으로 가입한다")
    void joinWithInputName() {
        long memberId = memberAuthService.joinWithInputName(KAKAO_ID, "존");

        assertThat(memberRepository.findById(memberId).orElseThrow().getName())
                .isEqualTo(MemberName.of("존"));
    }

    @Test
    @DisplayName("입력한 이름은 최대 5자 한글만 허용한다")
    void joinWithInvalidInputName() {
        assertThatThrownBy(() -> memberAuthService.joinWithInputName(KAKAO_ID, "John"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("이미 가입한 카카오 계정은 다시 가입되지 않는다")
    void joinTwice() {
        memberAuthService.joinWithKakaoName(KAKAO_ID, "홍길동");

        assertThatThrownBy(() -> memberAuthService.joinWithKakaoName(KAKAO_ID, "홍길동"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(() -> memberAuthService.joinWithInputName(KAKAO_ID, "존"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("가입한 카카오 계정이면 회원 ID를 찾고, 가입하지 않았으면 빈 값이다")
    void findMemberId() {
        long memberId = memberAuthService.joinWithKakaoName(KAKAO_ID, "홍길동").orElseThrow();

        assertThat(memberAuthService.findMemberId(KAKAO_ID)).contains(memberId);
        assertThat(memberAuthService.findMemberId(99999L)).isEmpty();
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ClockConfig.KST);
        }
    }
}
