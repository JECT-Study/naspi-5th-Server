package kr.ject.naspi.asset.domain;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 리그 시작 시 초기 자산(주식·현금) 배분 계산.
 *
 * <p>정책 (docs/policies/v1/domain/asset.md "초기 자산 배분"):
 * <ul>
 *   <li>종목마다 5,000주 발행. 상장자에게 2,500주, 나머지 2,500주는 상장자를 제외한 참여자에게 균등 배분하고
 *       균등 배분 후 남은 주식은 상장자에게 추가 배분한다.</li>
 *   <li>{@code 초기 현금 = 1억 원 - 배분받은 전체 주식 수 × 10,000원}</li>
 * </ul>
 */
public final class InitialAllocation {

    /** 종목당 발행 주식 수. */
    public static final long ISSUED_SHARES = 5_000;

    /** 상장자에게 먼저 배분하는 주식 수. */
    public static final long LISTER_SHARES = 2_500;

    /** 시초가 (1주당 원). */
    public static final long LISTING_PRICE = 10_000;

    /** 시초가 기준 초기 총자산 (원). */
    public static final long INITIAL_TOTAL_ASSET = 100_000_000;

    private final List<PlayerAllocation> allocations;

    private InitialAllocation(List<PlayerAllocation> allocations) {
        this.allocations = allocations;
    }

    /**
     * 상장 종목 목록으로 참여자별 초기 자산을 계산한다.
     *
     * @param listings 리그의 모든 상장 종목 (참여자당 1종목)
     * @return 참여자별 배분 결과
     * @throws IllegalArgumentException 종목이 2개 미만이거나, 종목·상장자가 중복된 경우
     */
    public static InitialAllocation calculate(List<StockListing> listings) {
        validate(listings);

        long others = listings.size() - 1L;

        // 균등 배분
        long sharesPerOther = (ISSUED_SHARES - LISTER_SHARES) / others;
        // 균등 배분 후 남은 주식 수
        long remainder = (ISSUED_SHARES - LISTER_SHARES) % others;

        // 모든 종목에 대해 자산 배분 처리
        List<PlayerAllocation> allocations = listings.stream()
                .map(StockListing::listerPlayerId)
                .map(playerId -> {
                    Map<Long, Long> shares = new LinkedHashMap<>();
                    for (StockListing listing : listings) {
                        // 본인인 경우 초기 배분 주식 + 잔여 주식 할당
                        // 아닌 경우, 미리 계산해놨던 균등 배분 값을 배정
                        long quantity = listing.listerPlayerId() == playerId
                                ? LISTER_SHARES + remainder
                                : sharesPerOther;
                        shares.put(listing.stockId(), quantity);
                    }
                    return PlayerAllocation.of(playerId, shares);
                })
                .toList();
        return new InitialAllocation(allocations);
    }

    private static void validate(List<StockListing> listings) {
        if (listings == null || listings.size() < 2) {
            throw new IllegalArgumentException("초기 자산 배분에는 상장 종목이 2개 이상 필요합니다.");
        }
        Set<Long> stockIds = new HashSet<>();
        Set<Long> listerIds = new HashSet<>();
        for (StockListing listing : listings) {
            if (!stockIds.add(listing.stockId())) {
                throw new IllegalArgumentException("종목이 중복되었습니다: " + listing.stockId());
            }
            if (!listerIds.add(listing.listerPlayerId())) {
                throw new IllegalArgumentException(
                        "참여자는 1종목만 상장할 수 있습니다: " + listing.listerPlayerId());
            }
        }
    }

    /**
     * 참여자별 배분 결과.
     *
     * @return 상장 순서대로 정렬된 참여자별 배분 결과
     */
    public List<PlayerAllocation> allocations() {
        return allocations;
    }

    /**
     * 참여자 한 명의 초기 자산.
     *
     * @param playerId 플레이어 ID
     * @param cash 초기 현금 (예수금)
     * @param shares 종목 ID별 배분 주식 수
     */
    public record PlayerAllocation(long playerId, long cash, Map<Long, Long> shares) {

        /**
         * 배분 주식 수로 초기 현금을 계산해 생성한다.
         *
         * @param playerId 플레이어 ID
         * @param shares 종목 ID별 배분 주식 수
         * @return 참여자 배분 결과
         */
        static PlayerAllocation of(long playerId, Map<Long, Long> shares) {
            long totalShares = shares.values().stream().mapToLong(Long::longValue).sum();
            long cash = INITIAL_TOTAL_ASSET - totalShares * LISTING_PRICE;
            return new PlayerAllocation(playerId, cash, Map.copyOf(shares));
        }
    }
}
