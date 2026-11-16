package kr.ject.naspi.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.global.exception.NaspiRuntimeException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보유 주식 (ERD: holding). 플레이어·종목당 1개.
 *
 * <p>예약 수량은 보유 수량 중 미체결 매도 주문에 묶인 수량이다.
 */
@Entity
@Table(name = "holding",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "stock_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    /** 자사주 최소 유지분 (주). */
    public static final long TREASURY_STOCK_MIN_SHARES = 1_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "stock_id", nullable = false)
    private Long stockId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Column(name = "inherited", nullable = false)
    private boolean inherited;

    /**
     * 매입 금액 (원). 보유 수량 전체를 사는 데 든 금액이며 이동평균법을 따른다
     * (docs/policies/v1/domain/asset.md "자산 조회"). 평가 손익의 기준값이다.
     * 체결 시 {@link #settleBuy}, {@link #settleSell}에서 갱신한다.
     */
    @Column(name = "purchase_amount", nullable = false)
    private long purchaseAmount;

    private Holding(long leagueId, long playerId, long stockId, long quantity) {
        this.leagueId = leagueId;
        this.playerId = playerId;
        this.stockId = stockId;
        this.quantity = quantity;
        this.reservedQuantity = 0;
        this.inherited = false;
        this.purchaseAmount = Math.multiplyExact(quantity, InitialAllocation.LISTING_PRICE);
    }

    /**
     * 초기 배분 주식으로 보유 주식을 만든다. 매입 금액은 {@code 배분 수량 × 시초가 10,000원}이다.
     *
     * @param leagueId 리그 ID
     * @param playerId 플레이어 ID
     * @param stockId 종목 ID
     * @param quantity 배분 수량
     * @return 보유 주식
     */
    public static Holding allocate(long leagueId, long playerId, long stockId, long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("배분 수량은 음수일 수 없습니다: " + quantity);
        }
        return new Holding(leagueId, playerId, stockId, quantity);
    }

    /**
     * 매도 주문 가능 수량. 예약 수량을 제외하고, 자사주는 최소 유지분 1,000주를 남긴다
     * (docs/policies/v1/domain/asset.md "주문 가능 자산 및 정산", "자사주 최소 유지분").
     *
     * @param treasuryStock 자사주(본인이 상장한 종목) 여부
     * @return 일반 종목은 {@code 보유 수량 - 예약 수량},
     *     자사주는 {@code max(0, 보유 수량 - 예약 수량 - 1,000주)}
     */
    public long orderableQuantity(boolean treasuryStock) {
        long available = quantity - reservedQuantity;
        if (!treasuryStock) {
            return available;
        }
        return Math.max(0, available - TREASURY_STOCK_MIN_SHARES);
    }

    /**
     * 매도 주문 수량만큼 주식을 예약한다. 매도 가능 수량({@link #orderableQuantity})을 넘으면 거절한다
     * (docs/policies/v1/domain/asset.md "주문 가능 자산 및 정산", "자사주 최소 유지분").
     * 호출하는 쪽은 보유 주식을 락으로 조회해야 한다.
     *
     * <p>체결은 예약된 수량 안에서만 보유 수량을 줄이므로(보유·예약이 같이 줄어듦), 접수 시 남긴 자사주
     * 최소 유지분은 체결 후에도 유지된다.
     *
     * @param orderQuantity 매도 주문 수량. 양수
     * @param treasuryStock 자사주(본인이 상장한 종목) 여부
     * @throws IllegalArgumentException 수량이 0 이하인 경우
     * @throws NaspiBizException 자사주 최소 유지분을 남기지 못하는 경우
     *     ({@link ErrorCode#TREASURY_STOCK_MIN_SHARES}), 그 밖에 매도 가능 수량이 부족한 경우
     *     ({@link ErrorCode#INSUFFICIENT_QUANTITY})
     */
    public void reserveForSell(long orderQuantity, boolean treasuryStock) {
        if (orderQuantity <= 0) {
            throw new IllegalArgumentException("매도 수량은 양수여야 합니다: " + orderQuantity);
        }
        if (orderQuantity > orderableQuantity(treasuryStock)) {
            boolean blockedByMinShares = treasuryStock
                    && orderQuantity <= quantity - reservedQuantity;
            throw new NaspiBizException(blockedByMinShares
                    ? ErrorCode.TREASURY_STOCK_MIN_SHARES : ErrorCode.INSUFFICIENT_QUANTITY);
        }
        reservedQuantity += orderQuantity;
    }

    /**
     * 취소·만료된 매도 주문의 미체결 잔량만큼 예약 수량을 해제한다
     * (docs/policies/v1/domain/asset.md "취소·만료"). 보유 수량은 바뀌지 않는다.
     * 호출하는 쪽은 보유 주식을 락으로 조회해야 한다.
     *
     * @param remainingQuantity 미체결 잔량. 양수
     * @throws IllegalArgumentException 수량이 0 이하인 경우
     * @throws NaspiRuntimeException 해제할 수량이 예약 수량보다 많은 경우 (예약·해제 기록이 어긋난 데이터 오류)
     */
    public void releaseForSell(long remainingQuantity) {
        if (remainingQuantity <= 0) {
            throw new IllegalArgumentException("해제 수량은 양수여야 합니다: " + remainingQuantity);
        }
        if (remainingQuantity > reservedQuantity) {
            throw new NaspiRuntimeException("해제할 수량이 예약 수량보다 많습니다: playerId=" + playerId
                    + ", stockId=" + stockId + ", reservedQuantity=" + reservedQuantity
                    + ", quantity=" + remainingQuantity);
        }
        reservedQuantity -= remainingQuantity;
    }

    /**
     * 매수 체결을 정산한다. 보유 수량을 늘리고 매입 금액에 {@code 체결가 × 체결 수량}을 더한다
     * (docs/policies/v1/domain/asset.md "체결 시 매입 금액 갱신"). 보유 수량이 0이었으면 매입 금액도 0이므로
     * 새로 계산하는 것과 같다. 호출하는 쪽은 보유 주식을 락으로 조회해야 한다.
     *
     * @param price 체결가. 양수
     * @param tradeQuantity 체결 수량. 양수
     * @throws IllegalArgumentException 가격이나 수량이 0 이하인 경우
     */
    public void settleBuy(long price, long tradeQuantity) {
        if (price <= 0 || tradeQuantity <= 0) {
            throw new IllegalArgumentException(
                    "가격과 수량은 양수여야 합니다: price=" + price + ", quantity=" + tradeQuantity);
        }
        quantity = Math.addExact(quantity, tradeQuantity);
        purchaseAmount = Math.addExact(purchaseAmount, Math.multiplyExact(price, tradeQuantity));
    }

    /**
     * 매도 체결을 정산한다. 체결 수량만큼 보유 수량과 예약 수량을 함께 줄이고, 매입 금액을
     * {@code 기존 매입 금액 × 매도 후 보유 수량 ÷ 매도 전 보유 수량}(원 미만 절사)으로 줄인다. 보유 수량이 0이 되면
     * 매입 금액도 0이다 (docs/policies/v1/domain/asset.md "체결 시 매입 금액 갱신").
     *
     * <p>매도는 예약된 수량 안에서만 체결한다. 그래서 {@code 보유 - 예약}이 바뀌지 않고, 접수 시 지킨 자사주 최소
     * 유지분이 체결 후에도 유지된다. 호출하는 쪽은 보유 주식을 락으로 조회해야 한다.
     *
     * @param tradeQuantity 체결 수량. 양수
     * @throws IllegalArgumentException 수량이 0 이하인 경우
     * @throws NaspiRuntimeException 체결 수량이 예약 수량보다 많은 경우 (데이터 오류)
     */
    public void settleSell(long tradeQuantity) {
        if (tradeQuantity <= 0) {
            throw new IllegalArgumentException("체결 수량은 양수여야 합니다: " + tradeQuantity);
        }
        if (tradeQuantity > reservedQuantity) {
            throw new NaspiRuntimeException("매도 체결 수량이 예약 수량보다 많습니다: playerId=" + playerId
                    + ", stockId=" + stockId + ", reservedQuantity=" + reservedQuantity
                    + ", quantity=" + tradeQuantity);
        }
        long remaining = quantity - tradeQuantity;
        purchaseAmount = remaining == 0
                ? 0
                : Math.multiplyExact(purchaseAmount, remaining) / quantity;
        quantity = remaining;
        reservedQuantity -= tradeQuantity;
    }
}
