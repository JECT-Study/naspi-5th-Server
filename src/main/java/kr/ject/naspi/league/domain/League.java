package kr.ject.naspi.league.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리그 (ERD: league). 친구끼리 7일간 거래하는 독립된 시장이며, 시작 전에는 대기실 역할을 한다.
 *
 * <p>방장 플레이어는 FK 없이 ID로만 참조한다. 플레이어가 리그 ID를 가져야 하므로 리그를 먼저 저장한 뒤
 * {@link #assignHost(long)}로 방장을 지정한다.
 */
@Entity
@Table(name = "league", uniqueConstraints = @UniqueConstraint(
        name = "uk_league_invite_code", columnNames = "invite_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class League {

    /** 한 리그에 입장할 수 있는 최대 인원 (참여자 + 대기자). */
    public static final int MAX_PLAYERS = 10;

    /** 리그를 시작하려면 필요한 최소 참여자 수. */
    public static final int MIN_PARTICIPANTS_TO_START = 3;

    /** 대기실 유효 기간. 생성 후 이 기간 안에 시작하지 않으면 대기실과 초대 코드가 만료된다. */
    public static final Duration WAITING_ROOM_LIFETIME = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private LeagueName name;

    /** 대기실 초대 코드. 리그가 시작되거나 대기실이 만료되면 비어 있다. */
    @Embedded
    private InviteCode inviteCode;

    @Column(name = "host_player_id")
    private Long hostPlayerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private LeagueStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 시작 일시. TODO: 리그 시작(LEA-09)에서 기록한다. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 종료 일시. TODO: 리그 종료(LEA-12)에서 기록한다. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    private League(LeagueName name, InviteCode inviteCode, LocalDateTime createdAt) {
        this.name = Objects.requireNonNull(name, "name");
        this.inviteCode = Objects.requireNonNull(inviteCode, "inviteCode");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = LeagueStatus.WAITING;
    }

    /**
     * 대기실 상태의 리그를 만든다.
     *
     * @param name 리그 이름
     * @param inviteCode 대기실 초대 코드
     * @param createdAt 생성 일시
     * @return 생성된 리그
     */
    public static League create(LeagueName name, InviteCode inviteCode, LocalDateTime createdAt) {
        return new League(name, inviteCode, createdAt);
    }

    /**
     * 방장을 지정한다.
     *
     * @param playerId 방장이 될 플레이어 ID
     */
    public void assignHost(long playerId) {
        this.hostPlayerId = playerId;
    }

    /**
     * 초대를 마감해 초대 코드를 비운다. 비워진 코드로는 입장할 수 없고, 다른 리그에 다시 발급될 수 있다.
     *
     * <p>TODO: 리그 시작(LEA-09)에서 호출한다.
     */
    public void closeInvitation() {
        this.inviteCode = null;
    }

    /**
     * 대기실이 만료되는 시각. 생성 후 7일이 지나는 순간부터 만료로 본다.
     *
     * @return 만료 일시
     */
    public LocalDateTime waitingRoomExpiresAt() {
        return createdAt.plus(WAITING_ROOM_LIFETIME);
    }

    /**
     * 만료된 대기실인지 확인한다. 만료 처리가 아직 저장되지 않았더라도 7일이 지났으면 만료로 본다.
     *
     * @param now 현재 일시
     * @return 만료 여부
     */
    public boolean isExpired(LocalDateTime now) {
        return status == LeagueStatus.EXPIRED
                || (status == LeagueStatus.WAITING && !now.isBefore(waitingRoomExpiresAt()));
    }

    /**
     * 초대 코드로 입장할 수 있는 대기실인지 확인한다.
     *
     * @param now 현재 일시
     * @return 시작 전이고 만료되지 않았으면 true
     */
    public boolean isOpenForInvitation(LocalDateTime now) {
        return status == LeagueStatus.WAITING && !isExpired(now);
    }

    /**
     * 생성 후 7일 안에 시작하지 않은 대기실을 만료시키고 초대 코드를 폐기한다.
     * 폐기된 코드는 다른 리그에 다시 발급될 수 있다.
     *
     * @param now 현재 일시
     * @throws IllegalStateException 대기실이 아니거나 아직 만료 시각이 되지 않은 경우
     */
    public void expire(LocalDateTime now) {
        if (status != LeagueStatus.WAITING || !isExpired(now)) {
            throw new IllegalStateException(
                    "만료할 수 없는 리그입니다. id=" + id + ", status=" + status);
        }
        this.status = LeagueStatus.EXPIRED;
        closeInvitation();
    }
}
