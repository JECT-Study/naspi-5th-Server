package kr.ject.naspi.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리그 이름.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 생성 및 초대"): 최대 12자, 모든 문자를 사용할 수 있고
 * 다른 리그와 이름이 같아도 된다. 비어 있는 이름은 받지 않는다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeagueName {

    /** 이름 최대 글자 수. */
    public static final int MAX_LENGTH = 12;

    // 이모지 등 4바이트 문자를 허용하므로 글자 수의 4배로 잡는다
    @Column(name = "name", nullable = false, length = MAX_LENGTH * 4)
    private String value;

    private LeagueName(String value) {
        this.value = value;
    }

    /**
     * 사용자가 입력한 이름으로 생성한다.
     *
     * @param input 입력한 이름
     * @return 리그 이름
     * @throws NaspiBizException 비어 있거나 12자를 넘는 경우
     */
    public static LeagueName of(String input) {
        if (input == null
                || input.isBlank()
                || input.codePointCount(0, input.length()) > MAX_LENGTH) {
            throw new NaspiBizException(ErrorCode.INVALID_LEAGUE_NAME);
        }
        return new LeagueName(input);
    }
}
