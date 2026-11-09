package kr.ject.naspi.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.regex.Pattern;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 종목 이름.
 *
 * <p>정책 (docs/policies/v1/domain/market.md "종목 상장"): 앞뒤 공백을 제거한 뒤 최대 12자이며,
 * 글자, 숫자, 공백만 사용할 수 있다 (특수문자·이모지 불가). 같은 리그 안의 중복 검사는 저장소가 맡는다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockName {

    /** 이름 최대 글자 수. */
    public static final int MAX_LENGTH = 12;

    private static final Pattern VALID = Pattern.compile("^[\\p{L}\\p{N} ]+$");

    @Column(name = "name", nullable = false, length = MAX_LENGTH * 4)
    private String value;

    private StockName(String value) {
        this.value = value;
    }

    /**
     * 사용자가 입력한 이름으로 생성한다. 앞뒤 공백은 제거하고, 제거한 글자 수로 길이를 확인한다.
     *
     * @param input 입력한 이름
     * @return 종목 이름
     * @throws NaspiBizException 비어 있거나 12자를 넘거나 허용하지 않는 문자를 포함한 경우
     */
    public static StockName of(String input) {
        if (input == null) {
            throw new NaspiBizException(ErrorCode.INVALID_STOCK_NAME);
        }
        String trimmed = input.strip();
        if (trimmed.isEmpty()
                || trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH
                || !VALID.matcher(trimmed).matches()) {
            throw new NaspiBizException(ErrorCode.INVALID_STOCK_NAME);
        }
        return new StockName(trimmed);
    }
}
