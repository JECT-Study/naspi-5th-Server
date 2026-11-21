package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueTradingService;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.domain.PriceLimit;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.MatchResult;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderBook;
import kr.ject.naspi.order.domain.OrderMatcher;
import kr.ject.naspi.order.domain.OrderRepository;
import kr.ject.naspi.order.domain.OrderStatus;
import kr.ject.naspi.order.domain.OrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지정가 주문 접수 (ORD-02), 미체결 주문 취소 (ORD-07).
 *
 * <p>접수는 한 트랜잭션에서 다음 순서로 처리한다. 순서는 리그 락 → asset 락으로 고정되어 있다
 * (docs/issues/v1/order.md ORD-03).
 * <ol>
 *   <li>리그 거래 락 (거래 가능 상태가 아니면 거절)</li>
 *   <li>당일 상·하한가 범위 확인</li>
 *   <li>자산 예약 (매수는 현금, 매도는 주식. 실패하면 거절)</li>
 *   <li>호가창 조회 → 주문 저장 → 매칭 → 체결마다 정산, 체결 기록 저장, 종목 현재가·거래량 갱신,
 *       {@link TradeExecuted} 발행</li>
 * </ol>
 * 미체결 잔여 수량은 대기 주문으로 남는다. 시간 우선순위가 주문 {@code id} 순서이므로 주문 저장은 반드시
 * 리그 락을 잡은 뒤에 한다.
 *
 * <p>취소도 같은 리그 락을 먼저 잡으므로 같은 리그의 접수·체결과 차례로 처리된다. 취소와 체결이 동시에
 * 요청되면 락을 먼저 잡은 쪽의 결과가 확정되고, 나중 쪽은 확정된 주문 상태를 보고 처리한다 (ORD-06).
 */
@Service
@RequiredArgsConstructor
public class LimitOrderService {

    private final LeagueTradingService leagueTradingService;
    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;
    private final OrderExecutionSupport support;
    private final OrderRepository orderRepository;

    /**
     * 지정가 주문을 접수하고 바로 매칭한다.
     *
     * <p>거래일은 락을 잡은 뒤 거래 가능 판정 시각으로 계산한다 (09:00:00부터 새 거래일). TODO: 거래일별 기준가
     * (MKT-06)가 아직 없어 임시로 인자로 받는다. 생기면 이 서비스가 직접 조회하도록 바꾸고 인자를 없앤다.
     *
     * @param command 주문 요청
     * @param basePrice 당일 기준가 (100원 이상, 100원 단위). 상·하한가 계산에 쓴다
     * @return 접수 직후 주문 상태
     * @throws NaspiBizException 가격·수량이 정책에 맞지 않거나, 거래할 수 없는 리그이거나, 종목이 리그에 없거나,
     *     가격이 상·하한가를 벗어나거나, 예약할 자산이 부족한 경우
     */
    @Transactional
    public PlacedOrder placeLimitOrder(PlaceLimitOrder command, long basePrice) {
        // 락 없이 확인할 수 있는 입력은 락을 잡기 전에 거른다.
        Order.validatePrice(command.price());
        Order.validateQuantity(command.quantity());

        // 접수 시각은 락을 잡은 뒤에 정한다. 락을 기다린 시간만큼 오래된 시각이 찍히면 09:00 전환 전 시각으로
        // 전환 후에 처리된 주문이 생길 수 있다. 거래 가능 판정 시각을 접수·체결 시각으로 그대로 쓴다.
        LocalDateTime now = leagueTradingService.lockForTrading(command.leagueId());
        int tradingDay = leagueQueryService.getTradingDayNumberAt(command.leagueId(), now);
        final Order order = Order.limit(command.leagueId(), command.playerId(), command.stockId(),
                command.side(), command.price(), command.quantity(), tradingDay, now);
        validateStockInLeague(command);
        if (!PriceLimit.of(basePrice).contains(command.price())) {
            throw new NaspiBizException(ErrorCode.ORDER_PRICE_OUT_OF_LIMIT);
        }
        support.reserve(order, command.price());

        OrderBook book = OrderBook.of(command.stockId(),
                orderRepository.findAllByStockIdAndTypeAndStatusInOrderByIdAsc(
                        command.stockId(), OrderType.LIMIT, OrderStatus.openStatuses()));
        orderRepository.save(order);
        MatchResult result = OrderMatcher.match(book, order, command.price());
        support.settleAll(result, now);
        // 자전거래로 취소된 신규 주문의 미체결 잔여 수량에 묶인 예약을 푼다.
        support.releaseRemaining(order);
        return PlacedOrder.from(order);
    }

    /**
     * 본인의 미체결 주문을 취소하고 미체결 잔여 수량에 묶인 예약 자산을 해제한다. 이미 체결된 부분은 유지된다.
     *
     * @param leagueId 리그 ID
     * @param playerId 취소하는 플레이어 ID
     * @param orderId 주문 ID
     * @return 취소 결과
     * @throws NaspiBizException 거래할 수 없는 리그이거나, 주문이 없거나 본인 주문이 아니거나
     *     ({@link ErrorCode#ORDER_NOT_FOUND}), 이미 체결 완료·취소·만료된 주문인 경우
     *     ({@link ErrorCode#INVALID_ORDER_STATUS_TRANSITION})
     */
    @Transactional
    public CanceledOrder cancelOrder(long leagueId, long playerId, long orderId) {
        // 락 대기 뒤의 거래 가능 판정 시각을 취소 시각으로 쓴다 (접수 경로와 같은 이유)
        LocalDateTime now = leagueTradingService.lockForTrading(leagueId);
        // 주문은 리그 락을 잡은 뒤에 읽으므로 직전에 커밋된 체결·취소가 반영된 상태를 본다. 그 사이 체결 완료되었거나
        // 이미 취소된 주문이면 상태 전환이 거절되어 예약이 두 번 해제되지 않는다.
        Order order = orderRepository.findWithLockById(orderId)
                .filter(o -> o.getLeagueId() == leagueId && o.getPlayerId() == playerId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.ORDER_NOT_FOUND));
        order.cancel(CancelReason.USER_REQUESTED, now);
        support.releaseRemaining(order);
        return CanceledOrder.from(order);
    }

    private void validateStockInLeague(PlaceLimitOrder command) {
        // 리그에 없는 종목이면 STOCK_NOT_FOUND. 매수는 asset 예약이 종목을 확인하지 않아 먼저 확인한다.
        stockQueryService.isListedBy(command.leagueId(), command.stockId(), command.playerId());
    }
}
