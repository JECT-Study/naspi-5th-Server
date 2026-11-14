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
        indexes = @Index(name = "idx_orders_book",
                columnList = "stock_id, side, status, price, id"))
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

    /** 이 주문에 묶인 예약 금액(원). 매수 주문의 예약 현금이며, 매도 주문은 0이다. */
    @Column(name = "reserved_amount", nullable = false)
    private long reservedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status;

    /** 접수 시점의 거래일 (1일차부터). 09:00 정각 이후 접수된 주문은 새 거래일 주문이다. */
    @Column(name = "trading_day", nullable = false)
    private int tradingDay;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

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
        this.reservedAmount = 0;
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
     * 호가창에 남아 체결을 기다리는 주문인지 확인한다. 시장가 주문은 호가창에 남지 않는다.
     *
     * @return 지정가이고 대기 또는 부분 체결 상태면 {@code true}
     */
    public boolean isResting() {
        return type == OrderType.LIMIT && status.isOpen();
    }

    /**
     * 예약 금액을 기록한다.
     *
     * @param amount 예약 금액 (0 이상)
     */
    public void reserve(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("예약 금액은 음수일 수 없습니다: " + amount);
        }
        this.reservedAmount = amount;
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
     * 미체결 잔여 수량을 취소한다. 이미 체결된 부분은 유지된다.
     *
     * @throws NaspiBizException 이미 종료된 주문인 경우
     */
    public void cancel() {
        transitionTo(OrderStatus.CANCELED);
    }

    /**
     * 미체결 잔여 수량을 만료시킨다. 이미 체결된 부분은 유지된다.
     *
     * @throws NaspiBizException 이미 종료된 주문인 경우
     */
    public void expire() {
        transitionTo(OrderStatus.EXPIRED);
    }

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new NaspiBizException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        this.status = next;
    }
}
