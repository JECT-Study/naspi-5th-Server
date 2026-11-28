package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.ject.naspi.league.application.LeagueTradingService;
import kr.ject.naspi.market.application.DailyPriceLimit;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.order.domain.CancelReason;
import kr.ject.naspi.order.domain.MarketOrderProtection;
import kr.ject.naspi.order.domain.MatchResult;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderBook;
import kr.ject.naspi.order.domain.OrderMatcher;
import kr.ject.naspi.order.domain.OrderRepository;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import kr.ject.naspi.order.domain.OrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시장가 주문 접수 (ORD-05).
 *
 * <p>접수는 한 트랜잭션에서 다음 순서로 처리한다. 락 순서는 지정가와 같이 리그 락 → asset 락이다.
 * <ol>
 *   <li>리그 거래 락, 거래일 전환 완료 확인과 거래일 번호 확정</li>
 *   <li>호가창 조회 → 최우선 상대 호가로 보호 가격을 한 번 확정 (이후 호가 변동은 반영하지 않는다)</li>
 *   <li>자산 예약. 매수는 보호 범위 안 매도 호가를 최우선부터 주문 수량까지 합산하고 부족한 수량은
 *       보호최고가로 계산한 현금, 매도는 수량만큼 주식 (docs/policies/v1/domain/asset.md "매수 주문의 현금
 *       예약"). 주문에 필요한 자산이 부족하면 수량을 줄이지 않고 주문을 거절한다</li>
 *   <li>주문 저장 → 보호 가격 안에서 매칭 → 체결마다 정산, 체결 기록 저장, 종목 현재가·거래량 갱신
 *       → 남은 예약 해제 (매수는 예약금 중 실제 총체결 금액을 제외한 나머지 전액, 매도는 취소된 잔량)</li>
 * </ol>
 * 상대 호가가 없으면 주문을 저장하고 자산을 예약하지 않은 채 전량 취소한다
 * ({@link CancelReason#MARKET_NOT_FILLED}).
 */
@Service
@RequiredArgsConstructor
public class MarketOrderService {

    private final LeagueTradingService leagueTradingService;
    private final StockQueryService stockQueryService;
    private final OrderExecutionSupport support;
    private final OrderRepository orderRepository;

    /**
     * 시장가 주문을 접수하고 바로 매칭한다.
     *
     * <p>보호 가격은 그 거래일의 상·하한가를 market에서 조회해 정한다.
     *
     * @param command 주문 요청
     * @return 접수 직후 주문 상태 ({@code FILLED} 또는 {@code CANCELED})
     * @throws kr.ject.naspi.global.exception.NaspiBizException 수량이 정책에 맞지 않거나, 거래할 수 없는
     *     리그이거나, 종목이 리그에 없거나, 거래일 전환 처리 전이거나, 예약할 자산이 부족한 경우
     */
    @Transactional
    public PlacedOrder placeMarketOrder(PlaceMarketOrder command) {
        Order.validateQuantity(command.quantity());

        LocalDateTime now = leagueTradingService.lockForTrading(command.leagueId());
        int tradingDay = leagueTradingService.requireTradingDayOpened(command.leagueId(), now);
        stockQueryService.isListedBy(command.leagueId(), command.stockId(), command.playerId());

        OrderBook book = OrderBook.of(command.stockId(),
                orderRepository.findAllByStockIdAndTypeAndStatusInOrderByIdAsc(
                        command.stockId(), OrderType.LIMIT, OrderStatus.openStatuses()));
        Optional<Long> bestOpposite = command.side() == OrderSide.BUY
                ? book.bestAsk() : book.bestBid();
        if (bestOpposite.isEmpty()) {
            // 보호 가격의 기준이 없으므로 체결 가능 수량이 0이다. 자산은 예약하지 않았으므로 해제도 없다.
            Order order = Order.market(command.leagueId(), command.playerId(),
                    command.stockId(), command.side(), command.quantity(), tradingDay, now);
            order.cancel(CancelReason.MARKET_NOT_FILLED, now);
            orderRepository.save(order);
            return PlacedOrder.from(order);
        }

        DailyPriceLimit limit = stockQueryService.getDailyPriceLimit(
                command.leagueId(), command.stockId(), tradingDay);
        long protection = MarketOrderProtection.calculate(
                command.side(), bestOpposite.get(), limit.lowerLimitPrice(),
                limit.upperLimitPrice());

        final Order order = Order.market(command.leagueId(), command.playerId(),
                command.stockId(), command.side(), command.quantity(), tradingDay, now);
        boolean buying = command.side() == OrderSide.BUY;
        long reservedAmount = 0;
        if (buying) {
            reservedAmount = MarketOrderProtection.buyReservationAmount(
                    book, command.quantity(), protection);
            support.reserveMarketBuy(order, reservedAmount);
        } else {
            support.reserve(order, protection);
        }
        orderRepository.save(order);
        MatchResult result = OrderMatcher.match(book, order, protection);
        support.settleAll(result, now);
        if (buying) {
            support.releaseMarketBuy(order, reservedAmount, result);
        } else {
            support.releaseRemaining(order);
        }
        return PlacedOrder.from(order);
    }
}
