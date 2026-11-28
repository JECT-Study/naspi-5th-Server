package kr.ject.naspi.league.domain;

/**
 * 순위가 매겨진 항목.
 *
 * @param rank 순위 (1부터). 공동 순위면 같은 값이다
 * @param entry 항목
 * @param <T> 항목 타입
 */
public record Ranked<T>(int rank, T entry) {

    /**
     * 순위를 검증한다.
     *
     * @throws IllegalArgumentException 순위가 1보다 작은 경우
     */
    public Ranked {
        if (rank < 1) {
            throw new IllegalArgumentException("순위는 1 이상이어야 합니다. rank=" + rank);
        }
    }
}
