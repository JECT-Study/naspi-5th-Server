package kr.ject.naspi.league.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingTest {

    /** 순위 대상. 이름은 나열 순서 확인용. */
    private record Entry(String name, long total) {
    }

    private static final Comparator<Entry> BY_NAME = Comparator.comparing(Entry::name);

    private static List<Ranked<Entry>> rank(Entry... entries) {
        return Ranking.rank(List.of(entries), Entry::total, BY_NAME);
    }

    @Test
    @DisplayName("총자산이 같으면 공동 순위이고, 다음 순위는 해당 순위에 동점자 수를 더한 순위다")
    void tiedRanksSkipNext() {
        assertThat(rank(new Entry("a", 300), new Entry("b", 300), new Entry("c", 200)))
                .extracting(Ranked::rank, r -> r.entry().name())
                .containsExactly(tuple(1, "a"), tuple(1, "b"), tuple(3, "c"));
    }

    @Test
    @DisplayName("중간 순위의 동점도 공동 순위로 처리하고 이후 순위를 건너뛴다")
    void tiedInMiddle() {
        assertThat(rank(new Entry("a", 500), new Entry("b", 400), new Entry("c", 400),
                new Entry("d", 400), new Entry("e", 100)))
                .extracting(Ranked::rank)
                .containsExactly(1, 2, 2, 2, 5);
    }

    @Test
    @DisplayName("모두 총자산이 같으면 모두 1위다")
    void allTied() {
        assertThat(rank(new Entry("a", 100), new Entry("b", 100), new Entry("c", 100)))
                .extracting(Ranked::rank)
                .containsExactly(1, 1, 1);
    }

    @Test
    @DisplayName("총자산이 높은 순서로 순위를 매긴다")
    void descendingOrder() {
        assertThat(rank(new Entry("a", 50_000_000), new Entry("b", 100_000_000),
                new Entry("c", 100_000_001)))
                .extracting(Ranked::rank, r -> r.entry().name())
                .containsExactly(tuple(1, "c"), tuple(2, "b"), tuple(3, "a"));
    }

    @Test
    @DisplayName("동점자의 나열 순서는 순위 값에 영향을 주지 않는다")
    void tieOrderDoesNotAffectRank() {
        List<Entry> entries = List.of(new Entry("a", 300), new Entry("b", 300),
                new Entry("c", 100));

        List<Ranked<Entry>> byName = Ranking.rank(entries, Entry::total, BY_NAME);
        List<Ranked<Entry>> reversed = Ranking.rank(entries, Entry::total, BY_NAME.reversed());

        assertThat(byName).extracting(r -> r.entry().name()).containsExactly("a", "b", "c");
        assertThat(reversed).extracting(r -> r.entry().name()).containsExactly("b", "a", "c");
        assertThat(byName).extracting(Ranked::rank).containsExactly(1, 1, 3);
        assertThat(reversed).extracting(Ranked::rank).containsExactly(1, 1, 3);
    }

    @Test
    @DisplayName("빈 입력은 빈 결과다")
    void empty() {
        assertThat(Ranking.rank(List.<Entry>of(), Entry::total, BY_NAME)).isEmpty();
    }

    @Test
    @DisplayName("순위는 1 이상이다")
    void rankMustBePositive() {
        assertThatThrownBy(() -> new Ranked<>(0, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
