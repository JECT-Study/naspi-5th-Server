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

class StockNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"가", "나스피", "Naspi", "나스피 2호", "열두글자이름입니다열두글", "あいう"})
    @DisplayName("종목 이름은 글자, 숫자, 중간 공백으로 12자까지 사용할 수 있다")
    void validName(String value) {
        assertThat(StockName.of(value).getValue()).isEqualTo(value);
    }

    @Test
    @DisplayName("종목 이름의 앞뒤 공백은 제거하고 제거한 글자 수로 12자 제한을 확인한다")
    void trimsBeforeCheckingLength() {
        // 12자 이름 앞뒤에 공백이 붙어도 통과하고 공백은 제거된다
        assertThat(StockName.of("  열두글자이름입니다열두글 ").getValue())
                .isEqualTo("열두글자이름입니다열두글");
    }

    @Test
    @DisplayName("종목 이름이 13자이면 INVALID_STOCK_NAME 예외가 발생한다")
    void tooLong() {
        assertInvalid("열세글자이름입니다열세글자");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "나스피!", "나스피#1", "a-b", "나스피😀", "나스피\t호"})
    @DisplayName("비어 있거나 특수문자·이모지가 있으면 INVALID_STOCK_NAME 예외가 발생한다")
    void invalidCharacters(String value) {
        assertInvalid(value);
    }

    @Test
    @DisplayName("같은 이름은 값이 같으면 동등하다 (앞뒤 공백 차이는 무시)")
    void equality() {
        assertThat(StockName.of(" 나스피 ")).isEqualTo(StockName.of("나스피"));
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> StockName.of(value))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STOCK_NAME);
    }
}
