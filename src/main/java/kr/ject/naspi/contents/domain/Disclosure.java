package kr.ject.naspi.contents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공시 (ERD: disclosure). 상장자가 자신의 일상을 자기 종목에 공유하는 글이다.
 *
 * <p>정책 (docs/policies/v1/domain/contents.md "공시"): 작성 횟수는 무제한이고, 작성한 뒤에는 수정할 수 없다.
 * 이미지는 {@link Image}로 따로 저장한다.
 */
@Entity
@Table(name = "disclosure",
        indexes = @Index(name = "idx_disclosure_stock_id", columnList = "stock_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Disclosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false, updatable = false)
    private Long leagueId;

    @Column(name = "stock_id", nullable = false, updatable = false)
    private Long stockId;

    @Column(name = "author_player_id", nullable = false, updatable = false)
    private Long authorPlayerId;

    @Embedded
    private DisclosureTitle title;

    @Embedded
    private DisclosureBody body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DisclosureStatus status;

    /** 작성한 거래일 번호 (1~7). */
    @Column(name = "trading_day", nullable = false, updatable = false)
    private int tradingDay;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Disclosure(long leagueId, long stockId, long authorPlayerId, DisclosureTitle title,
                       DisclosureBody body, int tradingDay, LocalDateTime createdAt) {
        this.leagueId = leagueId;
        this.stockId = stockId;
        this.authorPlayerId = authorPlayerId;
        this.title = Objects.requireNonNull(title, "title");
        this.body = Objects.requireNonNull(body, "body");
        this.status = DisclosureStatus.PUBLISHED;
        this.tradingDay = tradingDay;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 공시를 게시한다. 작성자가 종목의 상장자인지, 리그가 진행 중인지는 호출하는 쪽이 먼저 확인한다.
     *
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @param authorPlayerId 작성자(상장자) 플레이어 ID
     * @param title 제목
     * @param body 본문
     * @param tradingDay 작성한 거래일 번호
     * @param createdAt 작성 일시
     * @return 게시된 공시 (저장 전)
     */
    public static Disclosure publish(long leagueId, long stockId, long authorPlayerId,
                                     DisclosureTitle title, DisclosureBody body, int tradingDay,
                                     LocalDateTime createdAt) {
        return new Disclosure(leagueId, stockId, authorPlayerId, title, body, tradingDay,
                createdAt);
    }
}
