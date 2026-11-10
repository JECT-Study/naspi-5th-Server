package kr.ject.naspi.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리그 초대 코드. 리그(대기실)마다 공통 코드 1개를 발급하고, 다른 사용자는 이 코드로 대기실에 입장한다.
 *
 * <p>영문 대문자와 숫자 7자리이며, 아직 시작하지 않은 리그 사이에서 유일하다.
 * 컬럼은 UNIQUE(uk_league_invite_code)이면서 NULL을 허용하고,
 * 리그가 시작되거나 대기실이 만료되면 코드를 비운다({@link League#closeInvitation()}).
 * NULL끼리는 UNIQUE 제약에 걸리지 않으므로 대기실 리그 사이의 유일성만 DB가 보장하고,
 * 비워진 코드는 다른 리그에 다시 발급될 수 있다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode {

    /** 코드 길이. */
    public static final int LENGTH = 7;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9]{" + LENGTH + "}$");

    @Column(name = "invite_code", length = LENGTH)
    private String value;

    private InviteCode(String value) {
        this.value = value;
    }

    /**
     * 새 초대 코드를 무작위로 만든다.
     *
     * @param random 난수 생성기 (운영에서는 예측할 수 없는 {@code SecureRandom}을 쓴다)
     * @return 초대 코드
     */
    public static InviteCode random(RandomGenerator random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new InviteCode(code.toString());
    }

    /**
     * 사용자가 입력한 코드로 생성한다. 앞뒤 공백을 제거하고 소문자는 대문자로 바꿔 받는다.
     *
     * @param input 입력한 코드
     * @return 초대 코드
     * @throws NaspiBizException 영문·숫자 7자리가 아닌 경우
     */
    public static InviteCode of(String input) {
        if (input == null) {
            throw new NaspiBizException(ErrorCode.INVITE_CODE_NOT_FOUND);
        }
        String normalized = input.strip().toUpperCase(Locale.ROOT);
        if (!VALID.matcher(normalized).matches()) {
            throw new NaspiBizException(ErrorCode.INVITE_CODE_NOT_FOUND);
        }
        return new InviteCode(normalized);
    }
}
