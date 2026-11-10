package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LeagueNameTest {

    @Test
    @DisplayName("리그 이름은 최대 12자까지 입력할 수 있다")
    void maxLength() {
        String twelve = "가".repeat(12);

        assertThat(LeagueName.of(twelve).getValue()).isEqualTo(twelve);
    }

    @Test
    @DisplayName("리그 이름이 12자를 넘으면 생성할 수 없다")
    void overMaxLength() {
        assertThatThrownBy(() -> LeagueName.of("가".repeat(13)))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_LEAGUE_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"우리끼리 주식왕!", "League #1", "🚀🚀 떡상리그", "@_@;;"})
    @DisplayName("리그 이름에는 특수문자·이모지를 포함한 모든 문자를 사용할 수 있다")
    void anyCharacters(String name) {
        assertThat(LeagueName.of(name).getValue()).isEqualTo(name);
    }

    @Test
    @DisplayName("이모지는 한 글자로 센다 (12자 이모지 허용)")
    void emojiCountsAsOneCharacter() {
        String twelveEmoji = "🚀".repeat(12);

        assertThat(LeagueName.of(twelveEmoji).getValue()).isEqualTo(twelveEmoji);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("비어 있는 리그 이름은 사용할 수 없다")
    void blank(String name) {
        assertThatThrownBy(() -> LeagueName.of(name))
                .isInstanceOf(NaspiBizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_LEAGUE_NAME);
    }
}
