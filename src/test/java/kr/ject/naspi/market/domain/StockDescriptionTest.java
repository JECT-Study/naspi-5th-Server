package kr.ject.naspi.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StockDescriptionTest {

    @Test
    @DisplayName("종목 소개는 모든 문자를 사용해 50자까지 작성할 수 있다")
    void maxLength() {
        String fifty = "소".repeat(49) + "😀";

        assertThat(StockDescription.of(fifty).getValue()).isEqualTo(fifty);
    }

    @Test
    @DisplayName("종목 소개는 특수문자와 이모지를 포함할 수 있다")
    void allowsAnyCharacter() {
        assertThat(StockDescription.of("열심히 #가즈아! 🚀").getValue()).isEqualTo("열심히 #가즈아! 🚀");
    }

    @Test
    @DisplayName("종목 소개가 51자이면 INVALID_STOCK_DESCRIPTION 예외가 발생한다")
    void tooLong() {
        assertInvalid("소".repeat(51));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("종목 소개는 필수이므로 비어 있으면 INVALID_STOCK_DESCRIPTION 예외가 발생한다")
    void required(String value) {
        assertInvalid(value);
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> StockDescription.of(value))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STOCK_DESCRIPTION);
    }
}
