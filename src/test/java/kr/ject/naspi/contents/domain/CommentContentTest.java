package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommentContentTest {

    @Test
    @DisplayName("댓글은 최대 100자까지 작성할 수 있다")
    void maxLength() {
        assertThat(CommentContent.of("가".repeat(100)).getValue()).hasSize(100);
        assertThat(CommentContent.of("😀".repeat(100)).getValue()
                .codePointCount(0, 200)).isEqualTo(100);
        assertInvalid("가".repeat(101));
    }

    @Test
    @DisplayName("조합 이모지도 한 글자로 센다 (👍🏽 100개 허용, 101개 거부)")
    void compositeEmojiCountsAsOneCharacter() {
        String thumbsUp = "👍🏽";

        assertThat(CommentContent.of(thumbsUp.repeat(100)).getValue())
                .isEqualTo(thumbsUp.repeat(100));
        assertInvalid(thumbsUp.repeat(101));
    }

    @Test
    @DisplayName("빈 댓글은 작성할 수 없다")
    void blank() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("  ");
    }

    private static void assertInvalid(String input) {
        assertThatThrownBy(() -> CommentContent.of(input)).isInstanceOfSatisfying(
                NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_COMMENT_CONTENT));
    }
}
