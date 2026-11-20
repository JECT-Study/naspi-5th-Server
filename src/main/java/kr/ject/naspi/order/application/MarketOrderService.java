package kr.ject.naspi.order.application;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueTradingService;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.market.domain.PriceLimit;
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
 *   <li>리그 거래 락, 거래일 번호 확정</li>
 *   <li>호가창 조회 → 최우선 상대 호가로 보호 가격을 한 번 확정 (이후 호가 변동은 반영하지 않는다)</li>
 *   <li>자산 예약 (매수는 보호최고가 × 수량의 현금, 매도는 수량만큼 주식)</li>
 *   <li>주문 저장 → 보호 가격 안에서 매칭 → 체결마다 정산 → 취소된 잔량의 예약 해제</li>
 * </ol>
 * 상대 호가가 없으면 주문을 저장하고 자산을 예약하지 않은 채 전량 취소한다
 * ({@link CancelReason#MARKET_NOT_FILLED}).
 */
@Service
@RequiredArgsConstructor
public class MarketOrderService {

    private final LeagueTradingService leagueTradingService;
    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;
    private final OrderExecutionSupport support;
    private final OrderRepository orderRepository;

    /**
     * 시장가 주문을 접수하고 바로 매칭한다.
     *
     * <p>TODO(MKT-06): 거래일별 기준가가 없어 임시로 인자로 받고, 상·하한가도 market.domain.PriceLimit을
     * 직접 쓴다. MKT-06에서 market/application 조회로 교체한다.
     *
     * @param command 주문 요청
     * @param basePrice 당일 기준가 (100원 이상, 100원 단위). 상·하한가 계산에 쓴다
     * @return 접수 직후 주문 상태 ({@code FILLED} 또는 {@code CANCELED})
     * @throws kr.ject.naspi.global.exception.NaspiBizException 수량이 정책에 맞지 않거나, 거래할 수 없는
     *     리그이거나, 종목이 리그에 없거나, 예약할 자산이 부족한 경우
     */
    @Transactional
    public PlacedOrder placeMarketOrder(PlaceMarketOrder command, long basePrice) {
        Order.validateQuantity(command.quantity());

        LocalDateTime now = leagueTradingService.lockForTrading(command.leagueId());
        int tradingDay = leagueQueryService.getTradingDayNumberAt(command.leagueId(), now);
        final Order order = Order.market(command.leagueId(), command.playerId(),
                command.stockId(), command.side(), command.quantity(), tradingDay, now);
        stockQueryService.isListedBy(command.leagueId(), command.stockId(), command.playerId());

        OrderBook book = OrderBook.of(command.stockId(),
                orderRepository.findAllByStockIdAndTypeAndStatusInOrderByIdAsc(
                        command.stockId(), OrderType.LIMIT, OrderStatus.openStatuses()));
        Optional<Long> bestOpposite = command.side() == OrderSide.BUY
                ? book.bestAsk() : book.bestBid();
        if (bestOpposite.isEmpty()) {
            // 보호 가격의 기준이 없으므로 체결 가능 수량이 0이다. 자산은 예약하지 않았으므로 해제도 없다.
            order.cancel(CancelReason.MARKET_NOT_FILLED, now);
            orderRepository.save(order);
            return PlacedOrder.from(order);
        }

        // TODO(MKT-06): market.domain 직접 참조. 상·하한가 접근 경계는 MKT-06에서 정리한다.
        PriceLimit limit = PriceLimit.of(basePrice);
        long protection = MarketOrderProtection.calculate(
                command.side(), bestOpposite.get(), limit.lower(), limit.upper());

        support.reserve(order, protection);
        orderRepository.save(order);
        MatchResult result = OrderMatcher.match(book, order, protection);
        support.settleAll(result, now);
        support.releaseRemaining(order);
        return PlacedOrder.from(order);
    }
}
