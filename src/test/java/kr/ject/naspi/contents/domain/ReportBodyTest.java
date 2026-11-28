package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportBodyTest {

    @Test
    @DisplayName("제보 본문은 1자 이상 300자 이하만 허용한다 (이모지는 한 글자로 센다)")
    void length() {
        assertThat(ReportBody.of("가").getValue()).isEqualTo("가");
        String emoji300 = "😀".repeat(300);
        assertThat(ReportBody.of(emoji300).getValue()).isEqualTo(emoji300);

        assertInvalid("가".repeat(301));
        assertInvalid("😀".repeat(301));
    }

    @Test
    @DisplayName("공백만 있는 제보 본문은 빈 본문으로 보고 거절한다")
    void blank() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("   ");
    }

    private static void assertInvalid(String input) {
        assertThatThrownBy(() -> ReportBody.of(input)).isInstanceOfSatisfying(
                NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REPORT_BODY));
    }
}
