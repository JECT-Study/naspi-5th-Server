package kr.ject.naspi.order.application;

import java.util.List;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import kr.ject.naspi.order.domain.Order;
import kr.ject.naspi.order.domain.OrderFill;
import kr.ject.naspi.order.domain.OrderRepository;
import kr.ject.naspi.order.domain.OrderSide;
import kr.ject.naspi.order.domain.Trade;
import kr.ject.naspi.order.domain.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 내역 조회 (ORD-08).
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
        return OrderDetail.of(order, stockNameOf(leagueId, order.getStockId()),
                OrderFill.group(trades));
    }

    private String stockNameOf(long leagueId, long stockId) {
        return stockQueryService.findAllByLeagueId(leagueId).stream()
                .filter(stock -> stock.stockId() == stockId)
                .map(LeagueStock::name)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "주문의 종목을 리그에서 찾을 수 없습니다. stockId=" + stockId));
    }
}
