package kr.ject.naspi.order.application;

import java.util.List;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.order.application.OrderBookView.PriceLevel;
import kr.ject.naspi.order.domain.OrderBook;
import kr.ject.naspi.order.domain.OrderBookLevel;
import kr.ject.naspi.order.domain.OrderRepository;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.OrderStatus;
import kr.ject.naspi.order.domain.OrderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 호가 조회 (ORD-12).
 *
 * <p>정책 (docs/policies/v1/policy.md 5.4 호가 조회): 진행 중인 리그의 참여자는 리그 내 종목의 호가를 조회할 수
 * 있다. 대기 중인 지정가 주문의 미체결 잔여 수량을 가격별로 합산하고(주문자 구분 없음), 매도는 낮은 가격순,
 * 매수는 높은 가격순으로 각각 최대 5단계를 제공한다. 주문이 없는 쪽은 빈 목록이며 종목의 현재가를 함께 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderBookQueryService {

    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;
    private final OrderRepository orderRepository;

    /**
     * 로그인 회원이 진행 중인 리그의 종목 호가를 조회한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @return 호가와 현재가
     * @throws NaspiBizException 리그에 입장하지 않은 회원이거나({@link ErrorCode#FORBIDDEN}), 거래할 수 없는
     *     리그이거나, 리그에 없는 종목인 경우({@link ErrorCode#STOCK_NOT_FOUND})
     */
    public OrderBookView getOrderBook(long memberId, long leagueId, long stockId) {
        leagueQueryService.findPlayerId(memberId, leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));
        return getOrderBook(leagueId, stockId);
    }

    /**
     * 참여자 확인 없이 진행 중인 리그의 종목 호가를 조회한다. 구독 권한을 따로 확인하는 호출자(WebSocket)용이다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @return 호가와 현재가
     * @throws NaspiBizException 거래할 수 없는 리그이거나, 리그에 없는 종목인 경우
     *     ({@link ErrorCode#STOCK_NOT_FOUND})
     */
    public OrderBookView getOrderBook(long leagueId, long stockId) {
        leagueQueryService.requireTradable(leagueId);
        LeagueStock stock = stockQueryService.findAllByLeagueId(leagueId).stream()
                .filter(s -> s.stockId() == stockId)
                .findFirst()
                .orElseThrow(() -> new NaspiBizException(ErrorCode.STOCK_NOT_FOUND));
        if (stock.currentPrice() == null) {
            throw new IllegalStateException("진행 중인 리그 종목의 현재가가 없습니다: stockId=" + stockId);
        }
        OrderBook book = OrderBook.of(stockId,
                orderRepository.findAllByStockIdAndTypeAndStatusInOrderByIdAsc(stockId,
                        OrderType.LIMIT, OrderStatus.openStatuses()));
        return new OrderBookView(stock.currentPrice(),
                toLevels(book.levels(OrderSide.SELL, OrderBook.DISPLAY_DEPTH)),
                toLevels(book.levels(OrderSide.BUY, OrderBook.DISPLAY_DEPTH)));
    }

    private static List<PriceLevel> toLevels(List<OrderBookLevel> levels) {
        return levels.stream()
                .map(level -> new PriceLevel(level.price(), level.quantity()))
                .toList();
    }
}
