package kr.ject.naspi.contents.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;

/**
 * 통합 피드의 정렬 위치. 등록 일시 내림차순, 같으면 공시 먼저, 같으면 ID 내림차순으로 정렬한다.
 * 클라이언트에는 내용을 알 수 없는 문자열로 준다.
 *
 * @param at 등록 일시 (공시는 작성 일시, 뉴스는 발행 일시)
 * @param type 항목 종류
 * @param id 공시 ID 또는 뉴스 ID
 */
record FeedCursor(LocalDateTime at, FeedItemType type, long id) {

    /** 피드 정렬 순서 (앞이 최신). */
    static final Comparator<FeedCursor> ORDER = Comparator
            .comparing(FeedCursor::at, Comparator.reverseOrder())
            .thenComparingInt(cursor -> typeOrder(cursor.type()))
            .thenComparing(FeedCursor::id, Comparator.reverseOrder());

    private static final String SEPARATOR = "|";

    /**
     * 이 위치 다음에 오는 {@code target} 종류 항목 중 등록 일시가 이 위치와 같은 항목의 ID 상한을 구한다.
     *
     * @param target 찾을 항목 종류
     * @return ID 상한 (이 값 미만)
     */
    long idBefore(FeedItemType target) {
        int compared = Integer.compare(typeOrder(target), typeOrder(type));
        if (compared > 0) {
            return Long.MAX_VALUE;
        }
        return compared == 0 ? id : Long.MIN_VALUE;
    }

    /** 등록 일시가 같을 때 종류 순서 (작을수록 앞). */
    private static int typeOrder(FeedItemType type) {
        return switch (type) {
            case DISCLOSURE -> 0;
            case NEWS -> 1;
        };
    }

    /**
     * 클라이언트에 줄 커서 문자열로 바꾼다.
     *
     * @return URL에 그대로 쓸 수 있는 커서 문자열
     */
    String encode() {
        String raw = at + SEPARATOR + type + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 커서 문자열을 읽는다.
     *
     * @param cursor {@link #encode()}로 만든 문자열. 첫 페이지면 null
     * @return 정렬 위치. 첫 페이지면 null
     * @throws NaspiBizException 커서 형식이 올바르지 않은 경우
     */
    static FeedCursor decode(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("커서 구성 요소 수: " + parts.length);
            }
            return new FeedCursor(LocalDateTime.parse(parts[0]), FeedItemType.valueOf(parts[1]),
                    Long.parseLong(parts[2]));
        } catch (RuntimeException e) {
            throw new NaspiBizException(ErrorCode.INVALID_INPUT);
        }
    }
}
