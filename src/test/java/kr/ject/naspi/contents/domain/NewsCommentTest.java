package kr.ject.naspi.contents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NewsCommentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);

    @Test
    @DisplayName("댓글 작성자 본인만 댓글을 삭제할 수 있다")
    void onlyAuthorDeletes() {
        NewsComment comment = NewsComment.write(news(), 70L, CommentContent.of("댓글"), NOW);

        assertThatThrownBy(() -> comment.delete(71L)).isInstanceOfSatisfying(
                NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_AUTHOR));
        assertThat(comment.isDeleted()).isFalse();

        comment.delete(70L);
        assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);

        assertThatThrownBy(() -> comment.delete(70L)).isInstanceOfSatisfying(
                NaspiBizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("댓글에는 이미지를 첨부할 수 없고 대댓글을 달 수 없다")
    void noImageOrReply() {
        assertThat(NewsComment.class.getDeclaredFields())
                .extracting(field -> field.getName().toLowerCase())
                .noneMatch(name -> name.contains("image") || name.contains("parent"));
    }

    private static News news() {
        Disclosure disclosure = Disclosure.publish(3L, 11L, 70L, DisclosureTitle.of("제목"),
                DisclosureBody.of("본문"), 2, NOW);
        ReflectionTestUtils.setField(disclosure, "id", 1L);
        News news = News.requestFrom(disclosure, NOW);
        ReflectionTestUtils.setField(news, "id", 5L);
        return news;
    }
}
