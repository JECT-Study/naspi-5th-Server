package kr.ject.naspi.order.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderExecutionTotal;
import kr.ject.naspi.order.domain.OrderFill;
import kr.ject.naspi.order.domain.OrderRepository;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import kr.ject.naspi.order.domain.Trade;
import kr.ject.naspi.order.domain.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 내역 조회 (ORD-08 상세, ORD-13 목록).
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    /**
     * 로그인 회원이 진행 중인 리그에서 본인이 접수한 주문 1건의 접수·체결·취소·대기 내역을 조회한다.
     *
     * <p>체결은 주문 행 갱신과 체결 기록 저장을 한 트랜잭션에서 커밋한다. 주문과 체결 기록을 같은 스냅숏에서
     * 읽도록 REPEATABLE READ로 조회해 확정된 상태만 보인다. 락은 걸지 않는다. 회원·상대 플레이어는 조회하지
     * 않으므로 상대방이 탈퇴해도 영향이 없다.
     *
     * @param memberId 로그인한 회원 ID
     * @param leagueId 리그 ID
     * @param orderId 주문 ID
     * @return 주문 상세
     * @throws NaspiBizException 리그에 입장하지 않은 회원이거나({@link ErrorCode#FORBIDDEN}), 거래할 수 없는
     *     리그이거나, 주문이 없거나 본인 주문이 아닌 경우({@link ErrorCode#ORDER_NOT_FOUND})
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderDetail getOrderDetail(long memberId, long leagueId, long orderId) {
        long playerId = leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
        leagueQueryService.requireTradable(leagueId);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getLeagueId() == leagueId && o.getPlayerId() == playerId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.ORDER_NOT_FOUND));
        List<Trade> trades = order.getSide() == OrderSide.BUY
                ? tradeRepository.findAllByBuyOrderIdOrderByExecutedAtAscIdAsc(order.getId())
                : tradeRepository.findAllBySellOrderIdOrderByExecutedAtAscIdAsc(order.getId());
        return OrderDetail.of(order, stockNameIn(stockNamesOf(leagueId), order),
                OrderFill.group(trades));
    }

    /**
     * 로그인 회원이 진행 중인 리그에서 본인이 접수한 주문 목록을 접수 역순으로 조회한다. 대기 목록은 미체결
     * 잔여 수량이 있는 주문, 완료 목록은 수량의 일부 또는 전체가 체결·취소된 주문이다. 일부 체결 후 잔여
     * 수량이 대기 중인 주문은 두 목록에 모두 나온다.
     *
     * <p>주문 수와 무관하게 주문 1회, 체결 합계 최대 2회, 종목 1회로 조회한다. 주문과 체결 합계를 같은
     * 스냅숏에서 읽도록 REPEATABLE READ로 조회한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param leagueId 리그 ID
     * @param type 목록 종류
     * @param stockId 종목 ID. {@code null}이면 전체 종목
     * @return 주문 요약 목록. 접수 역순(id 내림차순)
     * @throws NaspiBizException 리그에 입장하지 않은 회원이거나({@link ErrorCode#FORBIDDEN}), 거래할 수 없는
     *     리그이거나, 리그에 없는 종목인 경우({@link ErrorCode#STOCK_NOT_FOUND})
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<OrderSummary> getOrders(long memberId, long leagueId, OrderListType type,
            Long stockId) {
        long playerId = leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
        leagueQueryService.requireTradable(leagueId);
        Map<Long, String> stockNames = stockNamesOf(leagueId);
        if (stockId != null && !stockNames.containsKey(stockId)) {
            throw new NaspiBizException(ErrorCode.STOCK_NOT_FOUND);
        }
        List<OrderStatus> statuses = type == OrderListType.PENDING
                ? OrderStatus.openStatuses() : OrderStatus.completedStatuses();
        List<Order> orders = stockId == null
                ? orderRepository.findAllByPlayerIdAndStatusInOrderByIdDesc(playerId, statuses)
                : orderRepository.findAllByPlayerIdAndStockIdAndStatusInOrderByIdDesc(playerId,
                        stockId, statuses);
        Map<Long, OrderExecutionTotal> totals = executionTotals(orders);
        return orders.stream()
                .map(order -> OrderSummary.of(order, stockNameIn(stockNames, order),
                        totals.getOrDefault(order.getId(),
                                OrderExecutionTotal.none(order.getId()))))
                .toList();
    }

    private Map<Long, OrderExecutionTotal> executionTotals(List<Order> orders) {
        List<Long> buyIds = new ArrayList<>();
        List<Long> sellIds = new ArrayList<>();
        for (Order order : orders) {
            (order.getSide() == OrderSide.BUY ? buyIds : sellIds).add(order.getId());
        }
        Map<Long, OrderExecutionTotal> totals = new HashMap<>();
        if (!buyIds.isEmpty()) {
            tradeRepository.sumByBuyOrderIds(buyIds)
                    .forEach(total -> totals.put(total.orderId(), total));
        }
        if (!sellIds.isEmpty()) {
            tradeRepository.sumBySellOrderIds(sellIds)
                    .forEach(total -> totals.put(total.orderId(), total));
        }
        return totals;
    }

    private String stockNameIn(Map<Long, String> stockNames, Order order) {
        String name = stockNames.get(order.getStockId());
        if (name == null) {
            throw new IllegalStateException(
                    "주문의 종목을 리그에서 찾을 수 없습니다. stockId=" + order.getStockId());
        }
        return name;
    }

    private Map<Long, String> stockNamesOf(long leagueId) {
        return stockQueryService.findAllByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(LeagueStock::stockId, LeagueStock::name));
    }
}
