package kr.ject.naspi.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 종목 소개.
 *
 * <p>정책 (docs/policies/v1/domain/market.md "종목 상장"): 필수 입력이며 최대 50자, 모든 문자를 사용할 수 있다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDescription {

    /** 소개 최대 글자 수. */
    public static final int MAX_LENGTH = 50;

    // 이모지 등 4바이트 문자를 허용하므로 글자 수의 4배로 잡는다
    @Column(name = "description", nullable = false, length = MAX_LENGTH * 4)
    private String value;

    private StockDescription(String value) {
        this.value = value;
    }

    /**
     * 사용자가 입력한 소개로 생성한다.
     *
     * @param input 입력한 소개
     * @return 종목 소개
     * @throws NaspiBizException 비어 있거나 50자를 넘는 경우
     */
    public static StockDescription of(String input) {
        if (input == null
                || input.isBlank()
                || input.codePointCount(0, input.length()) > MAX_LENGTH) {
            throw new NaspiBizException(ErrorCode.INVALID_STOCK_DESCRIPTION);
        }
        return new StockDescription(input);
    }
}
