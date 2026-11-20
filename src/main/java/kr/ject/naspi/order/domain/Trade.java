package kr.ject.naspi.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 체결 기록 (ERD: trade). 체결 1건 = 매수 주문 1개 + 매도 주문 1개.
 *
 * <p>정산과 같은 트랜잭션에서 저장하며, 한 번 저장하면 수정·삭제하지 않는다 (setter가 없다). 거래량 합계와
 * 리그 종료 평가(VWAP)의 원본이므로 회원이 탈퇴해도 남긴다. 회원(member)은 참조하지 않고 리그 플레이어
 * ID만 둔다. 거래일은 ERD의 거래일 FK 대신 {@link Order}와 같이 거래일 번호로 둔다.
 */
@Entity
@Table(name = "trade", indexes = {
        @Index(name = "idx_trade_stock_day", columnList = "stock_id, trading_day"),
        @Index(name = "idx_trade_stock_executed", columnList = "stock_id, executed_at, id"),
        @Index(name = "idx_trade_buy_order", columnList = "buy_order_id"),
        @Index(name = "idx_trade_sell_order", columnList = "sell_order_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade {

    /** 종료 평가(VWAP)에 필요한 최소 체결 수량 (주). 발행량 5,000주의 10%. */
    public static final long EVALUATION_MIN_QUANTITY = 500;

    /** {@link #EVALUATION_MIN_QUANTITY}를 채우는 데 필요한 최대 체결 건수. 체결 1건은 1주 이상이다. */
    public static final int VWAP_MAX_TRADES = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(name = "trading_day", nullable = false)
    private int tradingDay;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "buyer_player_id", nullable = false)
    private Long buyerPlayerId;

    @Column(name = "seller_player_id", nullable = false)
    private Long sellerPlayerId;

    @Column(name = "buy_order_id", nullable = false)
    private Long buyOrderId;

    @Column(name = "sell_order_id", nullable = false)
    private Long sellOrderId;

    /** 체결가 (원). */
    @Column(name = "price", nullable = false)
    private long price;

    /** 체결 수량 (주). */
    @Column(name = "quantity", nullable = false)
    private long quantity;

    /** 체결 금액 (원). {@code 체결가 × 체결 수량}. */
    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    private Trade(Execution execution, int tradingDay, LocalDateTime executedAt) {
        Order buy = execution.buyOrder();
        final Order sell = execution.sellOrder();
        this.leagueId = buy.getLeagueId();
        this.tradingDay = tradingDay;
        this.stockId = buy.getStockId();
        this.buyerPlayerId = buy.getPlayerId();
        this.sellerPlayerId = sell.getPlayerId();
        this.buyOrderId = buy.getId();
        this.sellOrderId = sell.getId();
        this.price = execution.price();
        this.quantity = execution.quantity();
        this.amount = execution.amount();
        this.executedAt = executedAt;
    }

    /**
     * 종료 시각에 가까운 체결부터 수량이 {@code limit}주가 될 때까지만 모은다. 마지막 체결 건은 한도를 채우는 데
     * 필요한 수량만 반영한다 (league.md "리그 결과"). 전체 수량이 한도에 못 미치면 전체를 쓴다.
     *
     * @param recentFirst 최근 체결부터 정렬된 체결 기록
     * @param limit 모을 수량 한도 (주)
     * @return 체결가와 반영 수량. 최근 체결부터의 순서이며 반영 수량 합계는 최대 {@code limit}이다
     */
    public static List<TradeSlice> takeRecentQuantity(List<Trade> recentFirst,
            long limit) {
        List<TradeSlice> slices = new ArrayList<>();
        long remaining = limit;
        for (Trade trade : recentFirst) {
            if (remaining <= 0) {
                break;
            }
            long taken = Math.min(trade.quantity, remaining);
            slices.add(new TradeSlice(trade.price, taken));
            remaining -= taken;
        }
        return slices;
    }

    /**
     * 매칭에서 나온 체결로 체결 기록을 만든다. 두 주문은 저장되어 ID가 있어야 한다.
     *
     * @param execution 체결
     * @param tradingDay 체결된 거래일 번호
     * @param executedAt 체결 일시
     * @return 체결 기록
     */
    public static Trade record(Execution execution, int tradingDay, LocalDateTime executedAt) {
        return new Trade(execution, tradingDay, executedAt);
    }
}
