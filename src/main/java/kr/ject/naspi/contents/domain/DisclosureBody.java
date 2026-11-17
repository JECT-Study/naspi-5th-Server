package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공시 본문.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): 최소 1자, 최대 300자. 공백만 있으면 빈 본문으로 본다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisclosureBody {

    /** 본문 최대 글자 수. */
    public static final int MAX_LENGTH = 300;

    // 이모지 등 4바이트 문자를 허용하므로 글자 수의 4배로 잡는다
    @Column(name = "body", nullable = false, length = MAX_LENGTH * 4)
    private String value;

    private DisclosureBody(String value) {
        this.value = value;
    }

    /**
     * 사용자가 입력한 본문으로 생성한다.
     *
     * @param input 입력한 본문
     * @return 공시 본문
     * @throws NaspiBizException 비어 있거나 300자를 넘는 경우
     */
    public static DisclosureBody of(String input) {
        if (input == null
                || input.isBlank()
                || input.codePointCount(0, input.length()) > MAX_LENGTH) {
            throw new NaspiBizException(ErrorCode.INVALID_DISCLOSURE_BODY);
        }
        return new DisclosureBody(input);
    }
}
