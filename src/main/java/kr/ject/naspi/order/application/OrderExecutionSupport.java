package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import kr.ject.naspi.asset.application.AssetReservationService;
import kr.ject.naspi.asset.application.AssetSettlementService;
import kr.ject.naspi.asset.application.TradeSettlement;
import kr.ject.naspi.market.application.StockExecutionService;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.Execution;
import kr.ject.naspi.order.domain.MatchResult;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.Trade;
import kr.ject.naspi.order.domain.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 지정가·시장가 접수·취소·만료 흐름이 함께 쓰는 자산 예약·정산·해제 처리.
 *
 * <p>트랜잭션은 호출하는 서비스가 연다.
 */
@Component
@RequiredArgsConstructor
class OrderExecutionSupport {

    private final AssetReservationService assetReservationService;
    private final AssetSettlementService assetSettlementService;
    private final StockExecutionService stockExecutionService;
    private final TradeRepository tradeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 주문에 필요한 자산을 예약한다. 매수는 {@code 예약 기준 가격 × 수량}의 현금을 예약하고 주문에
     * 예약 기준 가격을 남긴다. 매도는 주문 수량만큼 주식을 예약한다.
     *
     * @param order 접수한 주문
     * @param reservedPrice 매수 예약 기준 가격. 지정가는 주문 가격, 시장가는 보호최고가
     */
    void reserve(Order order, long reservedPrice) {
        if (order.getSide() == OrderSide.BUY) {
            assetReservationService.reserveCashForBuy(order.getLeagueId(), order.getPlayerId(),
                    reservedPrice, order.getQuantity());
            order.recordReservedPrice(reservedPrice);
        } else {
            assetReservationService.reserveStockForSell(order.getLeagueId(), order.getPlayerId(),
                    order.getStockId(), order.getQuantity());
        }
    }

    /**
     * 매칭 결과의 체결마다 정산하고 체결 기록을 저장하며 종목 현재가·거래량을 갱신하고
     * {@link TradeExecuted}를 발행한다.
     *
     * @param result 매칭 결과
     * @param executedAt 체결 시각
     */
    void settleAll(MatchResult result, LocalDateTime executedAt) {
        for (Execution execution : result.executions()) {
            settle(execution, executedAt);
        }
    }

    /**
     * 미체결 주문을 취소·만료로 종료하고 미체결 잔여 수량에 묶인 예약을 푼다. 사유가
     * {@link CancelReason#TRADING_DAY_EXPIRED}이면 만료, 아니면 취소로 처리한다. 이미 종료된 주문이면
     * 도메인이 상태 전이를 거절하고 해제는 일어나지 않는다. 종료한 뒤 {@link OrderCanceled}를 발행한다.
     *
     * @param order 주문
     * @param reason 종료 사유
     * @param closedAt 종료 시각
     */
    void closeRemaining(Order order, CancelReason reason, LocalDateTime closedAt) {
        if (reason == CancelReason.TRADING_DAY_EXPIRED) {
            order.expire(closedAt);
        } else {
            order.cancel(reason, closedAt);
        }
        releaseRemaining(order);
        eventPublisher.publishEvent(new OrderCanceled(order.getLeagueId(), order.getStockId(),
                order.getId(), order.getPlayerId(), reason));
    }

    /**
     * 취소·만료된 주문의 미체결 잔여 수량에 묶인 예약을 푼다. 취소·만료 수량이 없으면 아무것도 하지 않는다.
     *
     * @param order 주문
     */
    void releaseRemaining(Order order) {
        if (order.canceledQuantity() == 0) {
            return;
        }
        if (order.getSide() == OrderSide.BUY) {
            assetReservationService.releaseCashForBuy(order.getLeagueId(), order.getPlayerId(),
                    order.getReservedPrice(), order.getRemainingQuantity());
        } else {
            assetReservationService.releaseStockForSell(order.getLeagueId(), order.getPlayerId(),
                    order.getStockId(), order.getRemainingQuantity());
        }
    }

    private void settle(Execution execution, LocalDateTime executedAt) {
        Order buy = execution.buyOrder();
        Order sell = execution.sellOrder();
        assetSettlementService.settle(new TradeSettlement(buy.getLeagueId(), buy.getStockId(),
                buy.getPlayerId(), sell.getPlayerId(), buy.getReservedPrice(),
                execution.price(), execution.quantity()));
        // 체결 기록과 거래량은 정산과 같은 트랜잭션에서 같은 거래일로 남긴다. 거래일은 접수 시점의 거래일이다.
        int tradingDay = execution.incomingOrder().getTradingDay();
        tradeRepository.save(Trade.record(execution, tradingDay, executedAt));
        stockExecutionService.applyExecution(buy.getLeagueId(), buy.getStockId(), tradingDay,
                execution.price(), execution.quantity());
        eventPublisher.publishEvent(TradeExecuted.from(execution, executedAt));
    }
}
