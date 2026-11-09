package kr.ject.naspi.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * 종목 (ERD: stock). 리그의 참여자가 자신을 종목으로 상장한 것.
 *
 * <p>리그·상장자는 FK 없이 ID로만 참조한다. 같은 리그 안에서 이름은 중복될 수 없고,
 * 상장자(플레이어)는 하나의 종목만 상장할 수 있다. 서비스가 먼저 검증하고 DB 제약이 동시 요청을 막는다.
 */
@Entity
@Table(name = "stock",
        uniqueConstraints = @UniqueConstraint(columnNames = {"league_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(name = "lister_player_id", nullable = false, unique = true)
    private Long listerPlayerId;

    @Embedded
    private StockName name;

    @Embedded
    private StockDescription description;

    /**
     * 현재가 (1주당 원). 상장 시점에는 비어 있다.
     *
     * <p>TODO: 리그 시작 시 시초가 적용은 MKT-04, 체결 시 갱신은 MKT-05에서 구현한다.
     * 발행량·발행가는 정책상 고정값이라 저장하지 않고 상수로 계산한다 (InitialAllocation).
     */
    @Column(name = "current_price")
    private Long currentPrice;

    private Stock(long leagueId, long listerPlayerId, StockName name,
            StockDescription description) {
        this.leagueId = leagueId;
        this.listerPlayerId = listerPlayerId;
        this.name = name;
        this.description = description;
    }

    /**
     * 종목을 상장한다.
     *
     * @param leagueId 리그 ID
     * @param listerPlayerId 상장자 플레이어 ID
     * @param name 종목 이름
     * @param description 종목 소개
     * @return 상장된 종목
     */
    public static Stock list(long leagueId, long listerPlayerId, StockName name,
            StockDescription description) {
        return new Stock(leagueId, listerPlayerId, name, description);
    }
}
