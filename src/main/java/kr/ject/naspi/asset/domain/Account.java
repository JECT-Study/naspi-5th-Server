package kr.ject.naspi.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
