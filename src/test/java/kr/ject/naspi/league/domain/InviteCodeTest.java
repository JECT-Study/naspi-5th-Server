package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InviteCodeTest {

    @RepeatedTest(20)
    @DisplayName("초대 코드는 영문 대문자와 숫자 7자리로 발급된다")
    void randomFormat() {
        String code = InviteCode.random(new SecureRandom()).getValue();

        assertThat(code).matches("^[A-Z0-9]{7}$");
    }

    @Test
    @DisplayName("입력한 초대 코드는 앞뒤 공백을 무시하고 대소문자를 구분하지 않는다")
    void normalizeInput() {
        assertThat(InviteCode.of("  a1b2c3d ")).isEqualTo(InviteCode.of("A1B2C3D"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"A1B2C3", "A1B2C3D4", "A1B2-3D", "가나다라마바사"})
    @DisplayName("영문·숫자 7자리가 아닌 초대 코드는 유효하지 않다")
    void invalidInput(String input) {
        assertThatThrownBy(() -> InviteCode.of(input))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }
}
