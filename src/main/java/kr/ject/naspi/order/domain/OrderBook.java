package kr.ject.naspi.order.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 종목 하나의 호가창.
 *
 * <p>체결을 기다리는 지정가 주문만 담는다. 매수는 높은 가격 우선, 매도는 낮은 가격 우선이며,
 * 같은 가격에서는 먼저 접수된 주문(id가 작은 주문)이 앞선다
 * (docs/policies/v1/domain/order.md "체결").
 */
public final class OrderBook {

    /** 호가 조회에서 매도·매수 각각 제공하는 최대 가격 단계 수. */
    public static final int DISPLAY_DEPTH = 5;

    private final long stockId;
    private final List<Order> bids;
    private final List<Order> asks;

    private OrderBook(long stockId, List<Order> bids, List<Order> asks) {
        this.stockId = stockId;
        this.bids = bids;
        this.asks = asks;
    }

    /**
     * 체결 대기 중인 주문으로 호가창을 만든다. 호가창에 남지 않는 주문은 무시한다.
     *
     * @param stockId 종목 ID
     * @param orders 주문 목록 (순서 무관)
     * @return 호가창
     */
    public static OrderBook of(long stockId, List<Order> orders) {
        Comparator<Order> byTime = Comparator.comparing(Order::getId);
        List<Order> bids = orders.stream()
                .filter(o -> o.isResting() && o.getSide() == OrderSide.BUY)
                .sorted(Comparator.comparing(Order::getPrice).reversed().thenComparing(byTime))
                .toList();
        List<Order> asks = orders.stream()
                .filter(o -> o.isResting() && o.getSide() == OrderSide.SELL)
                .sorted(Comparator.comparing(Order::getPrice).thenComparing(byTime))
                .toList();
        return new OrderBook(stockId, bids, asks);
    }

    /**
     * 종목 ID.
     *
     * @return 종목 ID
     */
    public long stockId() {
        return stockId;
    }

    /**
     * 최우선 매수 호가.
     *
     * @return 가장 높은 매수 가격. 매수 주문이 없으면 비어 있다
     */
    public Optional<Long> bestBid() {
        return bids.stream().findFirst().map(Order::getPrice);
    }

    /**
     * 최우선 매도 호가.
     *
     * @return 가장 낮은 매도 가격. 매도 주문이 없으면 비어 있다
     */
    public Optional<Long> bestAsk() {
        return asks.stream().findFirst().map(Order::getPrice);
    }

    /**
     * 주어진 방향의 대기 주문을 체결 우선순위 순으로 반환한다.
     *
     * @param side 매매 구분
     * @return 매수는 높은 가격순, 매도는 낮은 가격순이며 같은 가격은 접수순
     */
    public List<Order> orders(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }

    /**
     * 주어진 방향의 가격 단계별 잔여 수량을 반환한다.
     *
     * @param side 매매 구분
     * @return 최우선 호가부터 나열한 가격 단계 (매수는 높은 가격순, 매도는 낮은 가격순)
     */
    public List<OrderBookLevel> levels(OrderSide side) {
        TreeMap<Long, long[]> byPrice = new TreeMap<>(side == OrderSide.BUY
                ? Comparator.<Long>reverseOrder() : Comparator.<Long>naturalOrder());
        for (Order order : orders(side)) {
            long[] sum = byPrice.computeIfAbsent(order.getPrice(), p -> new long[2]);
            sum[0] += order.getRemainingQuantity();
            sum[1]++;
        }
        List<OrderBookLevel> levels = new ArrayList<>();
        byPrice.forEach((price, sum) ->
                levels.add(new OrderBookLevel(price, sum[0], (int) sum[1])));
        return List.copyOf(levels);
    }

    /**
     * 주어진 방향의 가격 단계별 잔여 수량을 최우선 호가부터 최대 {@code maxLevels}단계까지 반환한다.
     *
     * @param side 매매 구분
     * @param maxLevels 최대 단계 수 (1 이상)
     * @return 최우선 호가부터 나열한 가격 단계. 주문이 없으면 빈 목록
     * @throws IllegalArgumentException 최대 단계 수가 1 미만인 경우
     */
    public List<OrderBookLevel> levels(OrderSide side, int maxLevels) {
        if (maxLevels <= 0) {
            throw new IllegalArgumentException("최대 단계 수는 1 이상이어야 합니다: " + maxLevels);
        }
        List<OrderBookLevel> all = levels(side);
        return all.size() <= maxLevels ? all : List.copyOf(all.subList(0, maxLevels));
    }
}
