package kr.ject.naspi.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TextLengthTest {

    /** 👨‍👩‍👧 (코드포인트 5개). */
    static final String FAMILY = "👨‍👩‍👧";

    /** 👩🏻‍❤️‍💋‍👨🏼 (코드포인트 10개, 표준 이모지 중 가장 긴 조합). */
    static final String KISS =
            "👩🏻‍❤️‍💋‍👨🏼";

    @ParameterizedTest
    @ValueSource(strings = {
        "가",
        "a",
        "😀",                           // 😀
        "❤️",                           // ❤️
        "👍🏽",               // 👍🏽
        "🇰🇷",               // 🇰🇷
        FAMILY,
        KISS,
    })
    @DisplayName("화면에 보이는 한 글자는 조합 이모지여도 1자로 센다")
    void countsGraphemeAsOne(String text) {
        assertThat(TextLength.count(text)).isEqualTo(1);
    }

    @Test
    @DisplayName("한글·영문·이모지가 섞인 문자열의 글자 수를 센다")
    void countsMixed() {
        assertThat(TextLength.count("떡상 " + FAMILY + "a")).isEqualTo(5);
        assertThat(TextLength.count("")).isZero();
    }

    @Test
    @DisplayName("최대 글자 수 이하이면 허용하고 넘으면 거부한다")
    void isWithin() {
        assertThat(TextLength.isWithin(FAMILY.repeat(12), 12)).isTrue();
        assertThat(TextLength.isWithin(KISS.repeat(12), 12)).isTrue();
        assertThat(TextLength.isWithin(FAMILY.repeat(13), 12)).isFalse();
    }

    @Test
    @DisplayName("한 글자에 결합 문자를 저장 한도 넘게 쌓으면 글자 수가 적어도 거부한다")
    void rejectsTooManyCodePoints() {
        String stacked = "a" + "́".repeat(TextLength.MAX_CODE_POINTS_PER_CHARACTER * 12);

        assertThat(TextLength.count(stacked)).isEqualTo(1);
        assertThat(TextLength.isWithin(stacked, 12)).isFalse();
    }
}
