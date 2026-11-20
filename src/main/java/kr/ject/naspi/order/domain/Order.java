package kr.ject.naspi.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 (ERD: orders).
 *
 * <p>정책 (docs/policies/v1/domain/order.md "주문"): 수량은 1주 단위의 양의 정수, 가격은 100원 이상의
 * 100원 단위이다. 시장가 주문은 가격을 지정하지 않으므로 {@code price}가 비어 있다.
 *
 * <p>시간 우선순위는 접수 순서이며, 접수 순서는 {@code id}(자동 증가)로 정한다.
 */
@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "idx_orders_book",
                        columnList = "stock_id, side, status, price, id"),
                @Index(name = "idx_orders_player", columnList = "player_id, status, id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    /** 호가 단위 (원). */
    public static final long TICK_SIZE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 4)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 6)
    private OrderType type;

    /** 주문 가격. 시장가 주문은 {@code null}이다. */
    @Column(name = "price")
    private Long price;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private long remainingQuantity;

    /**
     * 매수 주문의 예약 기준 가격(원). 접수 때 현금을 예약한 가격이며, 매도 주문은 {@code null}이다.
     *
     * <p>asset이 예약 기준 가격 × 수량 단위로 예약·해제·정산하므로 주문마다 가격을 남겨 둔다.
     * 지정가는 주문 가격, 시장가는 보호최고가이다.
     */
    @Column(name = "reserved_price")
    private Long reservedPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status;

    /** 접수 시점의 거래일 (1일차부터). 09:00 정각 이후 접수된 주문은 새 거래일 주문이다. */
    @Column(name = "trading_day", nullable = false)
    private int tradingDay;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    /** 취소·만료된 시각. 취소·만료되지 않은 주문은 {@code null}이다. */
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /** 취소·만료 사유. 취소·만료되지 않은 주문은 {@code null}이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 32)
    private CancelReason cancelReason;

    private Order(long leagueId, long playerId, long stockId, OrderSide side, OrderType type,
            Long price, long quantity, int tradingDay, LocalDateTime orderedAt) {
        this.leagueId = leagueId;
        this.playerId = playerId;
        this.stockId = stockId;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.PENDING;
        this.tradingDay = tradingDay;
        this.orderedAt = orderedAt;
    }

    /**
     * 지정가 주문을 만든다.
     *
     * @param leagueId 리그 ID
     * @param playerId 주문한 플레이어 ID
     * @param stockId 종목 ID
     * @param side 매매 구분
     * @param price 주문 가격 (100원 이상, 100원 단위)
     * @param quantity 주문 수량 (1주 이상)
     * @param tradingDay 접수 시점의 거래일
     * @param orderedAt 접수 시각
     * @return 대기 상태의 지정가 주문
     * @throws NaspiBizException 가격이나 수량이 정책에 맞지 않는 경우
     */
    public static Order limit(long leagueId, long playerId, long stockId, OrderSide side,
            long price, long quantity, int tradingDay, LocalDateTime orderedAt) {
        validatePrice(price);
        validateQuantity(quantity);
        return new Order(leagueId, playerId, stockId, side, OrderType.LIMIT, price, quantity,
                tradingDay, orderedAt);
    }

    /**
     * 시장가 주문을 만든다. 가격은 지정하지 않는다.
     *
     * @param leagueId 리그 ID
     * @param playerId 주문한 플레이어 ID
     * @param stockId 종목 ID
     * @param side 매매 구분
     * @param quantity 주문 수량 (1주 이상)
     * @param tradingDay 접수 시점의 거래일
     * @param orderedAt 접수 시각
     * @return 대기 상태의 시장가 주문
     * @throws NaspiBizException 수량이 정책에 맞지 않는 경우
     */
    public static Order market(long leagueId, long playerId, long stockId, OrderSide side,
            long quantity, int tradingDay, LocalDateTime orderedAt) {
        validateQuantity(quantity);
        return new Order(leagueId, playerId, stockId, side, OrderType.MARKET, null, quantity,
                tradingDay, orderedAt);
    }

    /**
     * 가격이 정책에 맞는지 검증한다.
     *
     * @param price 가격
     * @throws NaspiBizException 100원 미만이거나 100원 단위가 아닌 경우
     */
    public static void validatePrice(long price) {
        if (price < TICK_SIZE || price % TICK_SIZE != 0) {
            throw new NaspiBizException(ErrorCode.INVALID_ORDER_PRICE);
        }
    }

    /**
     * 수량이 정책에 맞는지 검증한다.
     *
     * @param quantity 수량
     * @throws NaspiBizException 1주 미만인 경우
     */
    public static void validateQuantity(long quantity) {
        if (quantity < 1) {
            throw new NaspiBizException(ErrorCode.INVALID_ORDER_QUANTITY);
        }
    }

    /**
     * 체결된 수량.
     *
     * @return {@code 주문 수량 - 잔여 수량}
     */
    public long filledQuantity() {
        return quantity - remainingQuantity;
    }

    /**
     * 취소 수량. 취소·만료 시점의 미체결 잔여 수량이며, 거래일 변경으로 만료된 수량도 취소로 합산한다.
     *
     * @return 취소·만료된 주문이면 잔여 수량, 아니면 0
     */
    public long canceledQuantity() {
        return status == OrderStatus.CANCELED || status == OrderStatus.EXPIRED
                ? remainingQuantity : 0;
    }

    /**
     * 현재 대기 수량.
     *
     * @return {@code 최초 주문 수량 - 체결 수량 - 취소 수량}
     */
    public long pendingQuantity() {
        return quantity - filledQuantity() - canceledQuantity();
    }

    /**
     * 호가창에 남아 체결을 기다리는 주문인지 확인한다. 시장가 주문은 호가창에 남지 않는다.
     *
     * @return 지정가이고 대기 또는 부분 체결 상태면 {@code true}
     */
    public boolean isResting() {
        return type == OrderType.LIMIT && status.isOpen();
    }

    /**
     * 매수 주문의 예약 기준 가격을 기록한다. 현금을 예약한 가격과 같아야 한다.
     *
     * @param price 예약 기준 가격 (양수)
     * @throws IllegalArgumentException 매도 주문이거나 가격이 양수가 아닌 경우
     */
    public void recordReservedPrice(long price) {
        if (side != OrderSide.BUY) {
            throw new IllegalArgumentException("예약 기준 가격은 매수 주문에만 기록합니다");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("예약 기준 가격은 양수여야 합니다: " + price);
        }
        this.reservedPrice = price;
    }

    /**
     * 미체결 잔여 수량에 묶여 있는 예약 현금.
     *
     * @return {@code 예약 기준 가격 × 잔여 수량}. 매도 주문이거나 예약 기준 가격이 없으면 0
     */
    public long reservedCash() {
        return reservedPrice == null ? 0 : Math.multiplyExact(reservedPrice, remainingQuantity);
    }

    /**
     * 체결을 반영한다. 잔여 수량이 0이 되면 체결 완료, 아니면 부분 체결이 된다.
     *
     * @param executedQuantity 체결 수량 (1주 이상, 잔여 수량 이하)
     * @throws NaspiBizException 이미 종료된 주문인 경우
     * @throws IllegalArgumentException 체결 수량이 잔여 수량을 넘거나 1주 미만인 경우
     */
    public void fill(long executedQuantity) {
        if (!status.isOpen()) {
            throw new NaspiBizException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        if (executedQuantity < 1 || executedQuantity > remainingQuantity) {
            throw new IllegalArgumentException("체결 수량이 올바르지 않습니다: " + executedQuantity
                    + " (잔여 " + remainingQuantity + ")");
        }
        OrderStatus next = executedQuantity == remainingQuantity
                ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        transitionTo(next);
        this.remainingQuantity -= executedQuantity;
    }

    /**
     * 미체결 잔여 수량을 취소한다. 이미 체결된 부분은 유지되며, 취소 수량은 이 시점의 잔여 수량이다.
     *
     * @param reason 취소 사유 ({@link CancelReason#TRADING_DAY_EXPIRED}는 {@link #expire}로만 기록한다)
     * @param canceledAt 취소 시각
     * @throws NaspiBizException 이미 종료된 주문인 경우
     * @throws IllegalArgumentException 사유가 거래일 변경 만료인 경우
     */
    public void cancel(CancelReason reason, LocalDateTime canceledAt) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(canceledAt, "canceledAt");
        if (reason == CancelReason.TRADING_DAY_EXPIRED) {
            throw new IllegalArgumentException("거래일 변경 만료는 expire로 기록합니다");
        }
        transitionTo(OrderStatus.CANCELED);
        this.canceledAt = canceledAt;
        this.cancelReason = reason;
    }

    /**
     * 미체결 잔여 수량을 만료시킨다. 이미 체결된 부분은 유지된다. 사유는
     * {@link CancelReason#TRADING_DAY_EXPIRED}로 기록한다.
     *
     * @param expiredAt 만료 시각. 거래일 전환 시각(09:00:00 KST)을 넘긴다. 만료 처리가 늦어져도 모든
     *     만료 주문이 같은 일시를 갖도록, ORD-09가 전환 시각을 인자로 넘긴다
     * @throws NaspiBizException 이미 종료된 주문인 경우
     */
    public void expire(LocalDateTime expiredAt) {
        Objects.requireNonNull(expiredAt, "expiredAt");
        transitionTo(OrderStatus.EXPIRED);
        this.canceledAt = expiredAt;
        this.cancelReason = CancelReason.TRADING_DAY_EXPIRED;
    }

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new NaspiBizException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = next;
    }
}
