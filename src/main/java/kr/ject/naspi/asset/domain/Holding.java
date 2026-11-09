package kr.ject.naspi.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

    private Holding(long leagueId, long playerId, long stockId, long quantity) {
        this.leagueId = leagueId;
        this.playerId = playerId;
        this.stockId = stockId;
        this.quantity = quantity;
        this.reservedQuantity = 0;
        this.inherited = false;
    }

    /**
     * 초기 배분 주식으로 보유 주식을 만든다.
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
}
