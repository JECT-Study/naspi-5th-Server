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
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
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

    /** 시작 일시. 방장이 시작 버튼을 누른 시각이며, 시작 전에는 비어 있다. */
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
     * 리그가 시작되거나 대기실이 만료될 때 호출된다.
     */
    public void closeInvitation() {
        this.inviteCode = null;
    }

    /**
     * 플레이어 구성이 리그 시작 조건을 만족하는지 확인한다.
     *
     * <p>정책 (docs/policies/v1/domain/league.md "대기실", "리그 시작 및 운영"): 방장 포함 상장을 마친 참여자가
     * 3명 이상이고, 대기자가 한 명도 없으며, 인원이 10명을 넘지 않아야 한다.
     *
     * @param players 리그에 입장한 플레이어 전원 (참여자 + 대기자)
     * @return 시작 가능 여부
     */
    public static boolean canStart(Collection<Player> players) {
        long participants = players.stream().filter(Player::isListed).count();  // 준비중인 플레이어가 수 확인
        boolean noWaiting = players.stream().allMatch(Player::isListed);        // 모든 플레이어가 준비했는지 확인
        return participants >= MIN_PARTICIPANTS_TO_START && noWaiting
                && players.size() <= MAX_PLAYERS;
    }

    /**
     * 리그를 시작한다. 시작 일시를 기록하고 초대 코드를 폐기하므로, 이후에는 아무도 새로 입장할 수 없다.
     *
     * <p>방장인지는 확인하지 않는다. 호출하는 쪽에서 요청한 플레이어가 방장인지 먼저 확인한다.
     *
     * @param players 리그에 입장한 플레이어 전원 (참여자 + 대기자)
     * @param now 시작 일시
     * @throws NaspiBizException 만료·삭제된 대기실이거나({@code LEAGUE_NOT_FOUND}), 이미 시작·종료된 리그이거나
     *         ({@code LEAGUE_ALREADY_STARTED}), 시작 조건을 만족하지 않는 경우({@code LEAGUE_NOT_STARTABLE})
     */
    public void start(Collection<Player> players, LocalDateTime now) {
        // 만료·삭제된 대기실
        if (isUnavailable(now)) {
            throw new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND);
        }
        // 이미 시작한 리그 (멱등 보장)
        if (status != LeagueStatus.WAITING) {
            throw new NaspiBizException(ErrorCode.LEAGUE_ALREADY_STARTED);
        }

        // 준비되지 않은 플레이어 존제
        if (!canStart(players)) {
            throw new NaspiBizException(ErrorCode.LEAGUE_NOT_STARTABLE);
        }
        this.status = LeagueStatus.STARTED;
        this.startedAt = now;
        closeInvitation();
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
     * 접근할 수 없는 리그인지 확인한다. 만료된 대기실과 삭제된 대기실은 없는 리그로 취급한다.
     *
     * @param now 현재 일시
     * @return 만료되었거나 삭제된 대기실이면 true
     */
    public boolean isUnavailable(LocalDateTime now) {
        return status == LeagueStatus.DELETED || isExpired(now);
    }

    /**
     * 플레이어가 대기실을 나간다. 나가는 플레이어가 방장이면 다음 방장에게 권한을 넘기고,
     * 넘겨받을 참여자가 없으면 대기실을 삭제하고 초대 코드를 폐기한다.
     *
     * <p>정책 (docs/policies/v1/domain/league.md "대기실", "리그 시작 및 운영"): 대기실의 사용자는 리그 시작 전에만
     * 나갈 수 있다. 방장이 나가면 상장을 먼저 마친 참여자가 새 방장이 되고, 그런 참여자가 없으면 대기실이 삭제된다.
     *
     * <p>플레이어 행과 종목을 지우는 일은 호출하는 쪽이 맡는다.
     *
     * @param leaver 나가는 플레이어
     * @param players 리그에 입장한 플레이어 전원 (나가는 플레이어 포함)
     * @param now 현재 일시
     * @return 대기실이 삭제되었으면 true
     * @throws NaspiBizException 만료·삭제된 대기실이거나({@code LEAGUE_NOT_FOUND}), 이미 시작·종료된 리그인 경우
     *         ({@code LEAGUE_ALREADY_STARTED})
     */
    public boolean leave(Player leaver, Collection<Player> players, LocalDateTime now) {
        if (isUnavailable(now)) {
            throw new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND);
        }
        // 리그 시작 이후에는 나갈 수 없다.
        if (status != LeagueStatus.WAITING) {
            throw new NaspiBizException(ErrorCode.LEAGUE_ALREADY_STARTED);
        }
        if (!leaver.getId().equals(hostPlayerId)) {
            return false;
        }

        // 나간 플레이어가 방장인 경우 방장 위임
        Optional<Player> nextHost = players.stream()
                .filter(Player::isListed)
                .filter(player -> !player.getId().equals(leaver.getId()))
                .min(Comparator.comparing(Player::getListedAt).thenComparing(Player::getId));
        if (nextHost.isPresent()) {
            assignHost(nextHost.get().getId());
            return false;
        }
        // 만약 위임할 다른 플레이어가 없는 경우 리그 삭제
        this.status = LeagueStatus.DELETED;
        this.hostPlayerId = null;
        closeInvitation();
        return true;
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
