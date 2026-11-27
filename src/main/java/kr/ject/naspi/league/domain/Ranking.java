package kr.ject.naspi.league.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * 총자산 순위 산정. 리그 결과(LEA-13)와 일일 랭킹(LEA-19)이 함께 쓴다.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 결과", "일일 랭킹"): 자사주를 포함한 총자산 기준으로 순위를
 * 산정한다. 총자산이 같으면 공동 순위이고, 공동 순위 이후의 순위는 해당 순위에 동점자 수를 더해 산정한다
 * (공동 1위 2명이면 다음은 3위). 공동 순위인 참여자의 나열 순서는 호출하는 쪽이 정한다 (상장 일시가 이른 순).
 */
public final class Ranking {

    private Ranking() {
    }

    /**
     * 점수가 높은 순서로 순위를 매긴다. 순위는 자신보다 점수가 높은 항목 수 + 1이다 (1, 1, 3).
     *
     * @param entries 순위를 매길 항목
     * @param score 항목의 점수 (예: 총자산)
     * @param tieOrder 점수가 같은 항목의 나열 순서. 순위 값에는 영향을 주지 않는다
     * @param <T> 항목 타입
     * @return 순위 순으로 정렬된 결과. 빈 입력이면 빈 목록
     */
    public static <T> List<Ranked<T>> rank(Collection<T> entries, ToLongFunction<T> score,
            Comparator<T> tieOrder) {
        List<T> sorted = entries.stream()
                .sorted(Comparator.comparingLong(score).reversed().thenComparing(tieOrder))
                .toList();
        List<Ranked<T>> ranked = new ArrayList<>(sorted.size());
        int rank = 0;
        long previousScore = 0;
        for (int i = 0; i < sorted.size(); i++) {
            T entry = sorted.get(i);
            long entryScore = score.applyAsLong(entry);
            if (i == 0 || entryScore != previousScore) {
                rank = i + 1;
                previousScore = entryScore;
            }
            ranked.add(new Ranked<>(rank, entry));
        }
        return List.copyOf(ranked);
    }
}
