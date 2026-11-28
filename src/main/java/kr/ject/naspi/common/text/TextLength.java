package kr.ject.naspi.common.text;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * 사용자 입력의 글자 수 계산.
 *
 * <p>화면에 보이는 한 글자(확장 자소 클러스터)를 1자로 센다. 조합 이모지(👨‍👩‍👧, 👍🏽, 🇰🇷)도 1자다.
 * 한 글자를 이루는 코드포인트 수에는 상한이 없으므로(결합 문자 중첩 등), 저장 컬럼 길이를 넘지 않도록
 * 코드포인트 수도 글자 수 × {@link #MAX_CODE_POINTS_PER_CHARACTER} 이하로 제한한다.
 */
public final class TextLength {

    /** 한 글자가 차지할 수 있는 최대 코드포인트 수. 표준 이모지 중 가장 긴 조합(10개)을 수용한다. */
    public static final int MAX_CODE_POINTS_PER_CHARACTER = 10;

    private TextLength() {
    }

    /**
     * 글자 수를 센다.
     *
     * @param text 문자열
     * @return 화면에 보이는 글자 수
     */
    public static int count(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        int count = 0;
        while (iterator.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }

    /**
     * 최대 글자 수 이하인지 확인한다. 코드포인트 수가 저장 한도를 넘어도 초과로 본다.
     *
     * @param text 문자열
     * @param maxLength 최대 글자 수
     * @return 최대 글자 수 이하이면 true
     */
    public static boolean isWithin(String text, int maxLength) {
        if (text.codePointCount(0, text.length()) > maxLength * MAX_CODE_POINTS_PER_CHARACTER) {
            return false;
        }
        return count(text) <= maxLength;
    }
}
