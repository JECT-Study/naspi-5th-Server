package kr.ject.naspi.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플레이어 (ERD: player). 리그에 입장한 사용자이며, 리그 안의 자산·종목·주문은 모두 플레이어가 소유한다.
 *
 * <p>대기실에 입장하면 대기자로 생기고, 종목 상장을 마치면 참여자가 된다.
 * 상장 완료 여부로 참여자와 대기자를 구분한다.
 * 방장이 대기실을 나가면 상장일시가 빠른 참여자부터 다음 방장이 된다.
 * 한 회원은 같은 리그에 한 번만 입장할 수 있다.
 */
@Entity
@Table(name = "player",
        uniqueConstraints = @UniqueConstraint(columnNames = {"league_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    /** 회원 ID. 봇이면 비어 있다. */
    @Column(name = "member_id")
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PlayerType type;

    /** 참여일시 (대기실에 입장한 일시). */
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /** 상장 완료 여부. 상장을 마친 참여자면 true, 대기자면 false. */
    @Column(name = "listed", nullable = false)
    private boolean listed;

    /** 상장일시 (종목 상장을 마쳐 참여자가 된 일시). 대기자는 비어 있다. */
    @Column(name = "listed_at")
    private LocalDateTime listedAt;

    private Player(long leagueId, long memberId, LocalDateTime joinedAt) {
        this.leagueId = leagueId;
        this.memberId = memberId;
        this.type = PlayerType.NORMAL;
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
        this.listed = false;
    }

    /**
     * 회원을 대기자로 리그에 입장시킨다.
     *
     * @param leagueId 리그 ID
     * @param memberId 회원 ID
     * @param joinedAt 참여일시
     * @return 대기자 플레이어
     */
    public static Player enter(long leagueId, long memberId, LocalDateTime joinedAt) {
        return new Player(leagueId, memberId, joinedAt);
    }

    /**
     * 종목 상장을 마쳐 참여자가 된다.
     *
     * @param listedAt 상장일시
     */
    public void completeListing(LocalDateTime listedAt) {
        this.listed = true;
        this.listedAt = Objects.requireNonNull(listedAt, "listedAt");
    }
}
