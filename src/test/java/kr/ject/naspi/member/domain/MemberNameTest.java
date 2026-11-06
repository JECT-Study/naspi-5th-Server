package kr.ject.naspi.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberNameTest {

    @Nested
    @DisplayName("직접 입력한 이름")
    class Of {

        @ParameterizedTest
        @ValueSource(strings = {"김", "홍길동", "다섯글자다"})
        @DisplayName("1~5자의 한글이면 생성된다")
        void validName(String value) {
            assertThat(MemberName.of(value).getValue()).isEqualTo(value);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"여섯글자이름", "Kim", "홍길동1", "홍 길동", "홍길동!", "金", "ㄱㄴ"})
        @DisplayName("한글이 아니거나 5자를 넘으면 INVALID_MEMBER_NAME 예외가 발생한다")
        void invalidName(String value) {
            assertThatThrownBy(() -> MemberName.of(value))
                    .isInstanceOf(NaspiBizException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_MEMBER_NAME);
        }

        @Test
        @DisplayName("같은 이름은 동등하다 (중복 허용 정책과 무관하게 값 비교)")
        void equality() {
            assertThat(MemberName.of("홍길동")).isEqualTo(MemberName.of("홍길동"));
        }
    }

    @Nested
    @DisplayName("카카오 이름 변환")
    class FromKakaoName {

        @ParameterizedTest
        @CsvSource({
            "홍길동, 홍길동",
            "남궁민수다섯글, 남궁민수다",
            "홍Gil동, 홍동",
            "Kim 철수, 철수",
            "홍길동ABC순신이, 홍길동순신"
        })
        @DisplayName("한글이 아닌 문자를 제외한 뒤 5자를 초과하는 부분을 절사한다")
        void normalize(String kakaoName, String expected) {
            assertThat(MemberName.fromKakaoName(kakaoName))
                    .hasValueSatisfying(name -> assertThat(name.getValue()).isEqualTo(expected));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"John Smith", "123", "ㅋㅋㅋ"})
        @DisplayName("한글이 없으면 빈 값을 반환해 직접 입력을 받게 한다")
        void noHangul(String kakaoName) {
            assertThat(MemberName.fromKakaoName(kakaoName)).isEmpty();
        }
    }
}
