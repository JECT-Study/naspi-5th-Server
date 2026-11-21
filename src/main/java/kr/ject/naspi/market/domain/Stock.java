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

    /** 시초가 (1주당 원). 모든 종목의 최초 가격이자 최초 거래일의 기준가다. */
    public static final long OPENING_PRICE = 10_000L;

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
     * 현재가 (1주당 원). 상장 시점에는 비어 있고, 리그 시작 시 시초가({@link #open()}), 이후 체결마다
     * 최신 체결가({@link #applyTrade(long)})가 된다.
     *
     * <p>발행량·발행가는 정책상 고정값이라 저장하지 않고 상수로 계산한다 (InitialAllocation).
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

    /**
     * 종목 이름과 소개를 수정한다. 이름 중복 검사는 호출하는 쪽이 맡는다.
     *
     * @param name 새 종목 이름
     * @param description 새 종목 소개
     */
    public void edit(StockName name, StockDescription description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 종목 소개만 수정한다.
     *
     * @param description 새 종목 소개
     */
    public void changeDescription(StockDescription description) {
        this.description = description;
    }

    /**
     * 리그 시작 시 거래를 연다. 현재가를 시초가로 정한다.
     *
     * <p>정책 (docs/policies/v1/domain/market.md "시장"): 모든 종목의 최초 가격은 시초가 1주당 10,000원이고,
     * 첫 체결 전 현재가와 최초 거래일의 기준가는 시초가다.
     *
     * @throws IllegalStateException 이미 거래가 열린 종목인 경우
     */
    public void open() {
        if (currentPrice != null) {
            throw new IllegalStateException("이미 거래가 열린 종목입니다: stockId=" + id);
        }
        this.currentPrice = OPENING_PRICE;
    }

    /**
     * 체결이 발생하면 현재가를 최신 체결가로 바꾼다.
     *
     * <p>정책 (docs/policies/v1/domain/market.md "시장"): 현재가는 최신 체결가다. 호가 단위와 가격제한폭은
     * 주문 접수에서 이미 검증되었으므로 다시 확인하지 않는다.
     *
     * @param price 체결가 (1주당 원)
     * @throws IllegalStateException 거래가 열리지 않은 종목인 경우
     * @throws IllegalArgumentException 체결가가 0 이하인 경우
     */
    public void applyTrade(long price) {
        if (currentPrice == null) {
            throw new IllegalStateException("거래가 열리지 않은 종목입니다: stockId=" + id);
        }
        if (price <= 0) {
            throw new IllegalArgumentException("체결가는 0보다 커야 합니다: " + price);
        }
        this.currentPrice = price;
    }

    /**
     * 종목을 상장한 플레이어인지 확인한다.
     *
     * @param playerId 플레이어 ID
     * @return 상장자이면 {@code true}
     */
    public boolean isListedBy(long playerId) {
        return listerPlayerId == playerId;
    }
}
