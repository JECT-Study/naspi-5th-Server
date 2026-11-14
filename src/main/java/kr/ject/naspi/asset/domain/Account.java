package kr.ject.naspi.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 현금 계좌 (ERD: account). 플레이어당 1개.
 *
 * <p>예수금은 보유 현금 전체, 예약금은 그중 미체결 매수 주문에 묶인 금액이다.
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(name = "player_id", nullable = false, unique = true)
    private Long playerId;

    @Column(name = "deposit", nullable = false)
    private long deposit;

    @Column(name = "reserved_deposit", nullable = false)
    private long reservedDeposit;

    private Account(long leagueId, long playerId, long deposit) {
        this.leagueId = leagueId;
        this.playerId = playerId;
        this.deposit = deposit;
        this.reservedDeposit = 0;
    }

    /**
     * 초기 현금으로 계좌를 개설한다.
     *
     * @param leagueId 리그 ID
     * @param playerId 플레이어 ID
     * @param initialCash 초기 현금
     * @return 계좌
     */
    public static Account open(long leagueId, long playerId, long initialCash) {
        if (initialCash < 0) {
            throw new IllegalArgumentException("초기 현금은 음수일 수 없습니다: " + initialCash);
        }
        return new Account(leagueId, playerId, initialCash);
    }

    /**
     * 주문 가능 현금. 예약 자산을 제외한 잔액이다 (docs/policies/v1/domain/asset.md "주문 가능 자산 및 정산").
     *
     * @return {@code 예수금 - 예약금}
     */
    public long orderableDeposit() {
        return deposit - reservedDeposit;
    }

    /**
     * 매수 주문에 쓸 현금 {@code 예약 기준 가격 × 주문 수량}을 예약한다. 주문 가능 현금을 넘으면 거절한다
     * (docs/policies/v1/domain/asset.md "매수 주문의 현금 예약"). 호출하는 쪽은 계좌를 락으로 조회해야 한다.
     *
     * @param price 예약 기준 가격. 양수
     * @param quantity 주문 수량. 양수
     * @return 예약한 금액
     * @throws IllegalArgumentException 가격이나 수량이 0 이하인 경우
     * @throws NaspiBizException 주문 가능 현금이 부족한 경우 ({@link ErrorCode#INSUFFICIENT_DEPOSIT})
     */
    public long reserveForBuy(long price, long quantity) {
        if (price <= 0 || quantity <= 0) {
            throw new IllegalArgumentException(
                    "가격과 수량은 양수여야 합니다: price=" + price + ", quantity=" + quantity);
        }
        // 곱하기 전에 나눗셈으로 비교해 가격 × 수량이 long 범위를 넘는 경우도 함께 거절한다.
        if (quantity > orderableDeposit() / price) {
            throw new NaspiBizException(ErrorCode.INSUFFICIENT_DEPOSIT);
        }
        long amount = price * quantity;
        reservedDeposit += amount;
        return amount;
    }
}
