package kr.ject.naspi.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Optional;
import java.util.regex.Pattern;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 이름.
 *
 * <p>정책 (docs/policies/v1/member.md "회원 정보 - 이름"):
 * 최대 5자의 한글로만 구성하며 중복을 허용한다. 영어, 한자, 특수문자, 공백 등 한글이 아닌 문자는 사용할 수 없다.
 * 자음 혹은 모음만 있는 글자(ㄱ, ㅏ 등, 호환용 자모)도 한글 한 글자로 취급한다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberName {

    /** 이름 최대 글자 수. */
    public static final int MAX_LENGTH = 5;

    /** 한글 한 글자: 완성형 음절(가-힣)과 현대 자음·모음(ㄱ-ㅎ, ㅏ-ㅣ). */
    private static final String HANGUL = "가-힣ㄱ-ㅎㅏ-ㅣ";

    private static final Pattern VALID =
            Pattern.compile("^[" + HANGUL + "]{1," + MAX_LENGTH + "}$");
    private static final Pattern NON_HANGUL = Pattern.compile("[^" + HANGUL + "]");

    @Column(name = "name", nullable = false, length = MAX_LENGTH)
    private String value;

    private MemberName(String value) {
        this.value = value;
    }

    /**
     * 사용자가 입력한 이름으로 생성한다.
     *
     * @param value 이름
     * @return 회원 이름
     * @throws NaspiBizException 1~5자의 한글이 아닌 경우
     */
    public static MemberName of(String value) {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new NaspiBizException(ErrorCode.INVALID_MEMBER_NAME);
        }
        return new MemberName(value);
    }

    /**
     * 카카오 이름을 서비스 이름 규칙에 맞게 변환한다.
     *
     * <p>한글이 아닌 문자를 제외한 뒤 5자를 초과하는 부분을 절사한다.
     * 한글이 하나도 없으면 빈 값을 반환하며, 이 경우 회원가입 단계에서 이름을 직접 입력받는다.
     *
     * @param kakaoName 카카오 계정 이름
     * @return 변환된 회원 이름, 한글이 없으면 빈 값
     */
    public static Optional<MemberName> fromKakaoName(String kakaoName) {
        if (kakaoName == null) {
            return Optional.empty();
        }
        String hangul = NON_HANGUL.matcher(kakaoName).replaceAll("");
        if (hangul.isEmpty()) {
            return Optional.empty();
        }
        int length = Math.min(MAX_LENGTH, hangul.length());
        return Optional.of(new MemberName(hangul.substring(0, length)));
    }
}
