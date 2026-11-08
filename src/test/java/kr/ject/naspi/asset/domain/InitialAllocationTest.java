package kr.ject.naspi.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.LongStream;
import kr.ject.naspi.asset.domain.InitialAllocation.PlayerAllocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class InitialAllocationTest {

    @ParameterizedTest(name = "참여자 {0}명")
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, 10})
    @DisplayName("종목마다 5,000주를 발행해 참여자에게 모두 배분한다")
    void issuesAllShares(int playerCount) {
        InitialAllocation allocation = InitialAllocation.calculate(listings(playerCount));

        for (StockListing listing : listings(playerCount)) {
            long distributed = allocation.allocations().stream()
                    .mapToLong(player -> player.shares().get(listing.stockId()))
                    .sum();
            assertThat(distributed).isEqualTo(5_000);
        }
    }

    @ParameterizedTest(name = "참여자 {0}명: 상장자 {1}주, 다른 참여자 {2}주")
    @CsvSource({
        "3, 2500, 1250",
        "4, 2501, 833",
        "5, 2500, 625",
        "6, 2500, 500",
        "7, 2504, 416",
        "8, 2501, 357",
        "9, 2504, 312",
        "10, 2507, 277",
    })
    @DisplayName("상장자는 2,500주와 균등 배분 후 남은 주식을, 다른 참여자는 2,500주를 균등 배분받는다")
    void listerAndOthersShares(int playerCount, long listerShares, long otherShares) {
        InitialAllocation allocation = InitialAllocation.calculate(listings(playerCount));

        for (PlayerAllocation player : allocation.allocations()) {
            for (StockListing listing : listings(playerCount)) {
                long expected = listing.listerPlayerId() == player.playerId()
                        ? listerShares : otherShares;
                assertThat(player.shares().get(listing.stockId())).isEqualTo(expected);
            }
        }
    }

    @ParameterizedTest(name = "참여자 {0}명")
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, 10})
    @DisplayName("초기 현금은 1억 원에서 배분받은 전체 주식 수 × 10,000원을 뺀 금액이다")
    void initialCash(int playerCount) {
        InitialAllocation allocation = InitialAllocation.calculate(listings(playerCount));

        assertThat(allocation.allocations()).hasSize(playerCount);
        for (PlayerAllocation player : allocation.allocations()) {
            long totalShares = player.shares().values().stream().mapToLong(Long::longValue).sum();
            assertThat(player.cash()).isEqualTo(100_000_000 - totalShares * 10_000);
            assertThat(player.cash() + totalShares * 10_000).isEqualTo(100_000_000);
        }
    }

    @Test
    @DisplayName("상장 종목이 2개 미만이면 배분할 수 없다")
    void tooFewListings() {
        assertThatThrownBy(() -> InitialAllocation.calculate(listings(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InitialAllocation.calculate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("한 참여자가 2개 이상 종목의 상장자이면 배분할 수 없다")
    void duplicateLister() {
        List<StockListing> listings = List.of(
                new StockListing(1, 10), new StockListing(2, 10), new StockListing(3, 30));

        assertThatThrownBy(() -> InitialAllocation.calculate(listings))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 종목이 중복되면 배분할 수 없다")
    void duplicateStock() {
        List<StockListing> listings = List.of(
                new StockListing(1, 10), new StockListing(1, 20), new StockListing(3, 30));

        assertThatThrownBy(() -> InitialAllocation.calculate(listings))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 종목 ID 100+i, 상장자 플레이어 ID i. */
    private static List<StockListing> listings(int playerCount) {
        return LongStream.rangeClosed(1, playerCount)
                .mapToObj(i -> new StockListing(100 + i, i))
                .toList();
    }
}
