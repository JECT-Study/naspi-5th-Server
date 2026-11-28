package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import kr.ject.naspi.common.text.TextLength;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 내용.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "댓글 (공통)"): 최대 100자, 이미지 첨부 불가. 공백만 있으면 빈
 * 댓글로 본다. 공시 댓글도 같은 규칙이라 이름을 중립으로 둔다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentContent {

    /** 댓글 최대 글자 수. */
    public static final int MAX_LENGTH = 100;

    // 한 글자가 코드포인트 여러 개일 수 있으므로(조합 이모지 등) 한 글자 최대치로 잡는다. 작성 후 수정 불가 (updatable = false)
    @Column(name = "content", nullable = false, updatable = false,
            length = MAX_LENGTH * TextLength.MAX_CODE_POINTS_PER_CHARACTER)
    private String value;

    private CommentContent(String value) {
        this.value = value;
    }

    /**
     * 사용자가 입력한 내용으로 생성한다.
     *
     * @param input 입력한 댓글
     * @return 댓글 내용
     * @throws NaspiBizException 비어 있거나 100자를 넘는 경우
     */
    public static CommentContent of(String input) {
        if (input == null
                || input.isBlank()
                || !TextLength.isWithin(input, MAX_LENGTH)) {
            throw new NaspiBizException(ErrorCode.INVALID_COMMENT_CONTENT);
        }
        return new CommentContent(input);
    }
}
