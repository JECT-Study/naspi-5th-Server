package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import kr.ject.naspi.league.domain.TradingSchedule;
import kr.ject.naspi.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 정보와 플레이어 목록 조회.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "대기실"): 대기실에 진입한 사용자는 상장 완료 여부에 따라
 * 대기자 또는 참여자로 구분하고, 참여자 3명 이상이고 대기자가 없으면 리그를 시작할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeagueQueryService {

    private final LeagueRepository leagueRepository;
    private final PlayerRepository playerRepository;
    private final MemberService memberService;
    private final Clock clock;

    /**
     * 초대 코드로 리그 간단 정보를 조회한다. 입장하기 전에 어떤 리그인지 확인하는 용도로,
     * 로그인한 회원이면 누구나 조회할 수 있다.
     *
     * @param inviteCode 입력한 초대 코드 (대소문자 구분 없음)
     * @return 리그 이름, 상태, 입장 인원, 참여자 수
     * @throws NaspiBizException 초대 코드에 해당하는 리그가 없거나 형식이 틀렸거나
     *         ({@code INVITE_CODE_NOT_FOUND}), 삭제·만료된 대기실이거나({@code LEAGUE_NOT_FOUND}),
     *         이미 시작·종료된 리그인 경우({@code LEAGUE_ALREADY_STARTED})
     */
    public LeagueSummary getSummary(String inviteCode) {
        League league = leagueRepository.findByInviteCode(InviteCode.of(inviteCode))
                .orElseThrow(() -> new NaspiBizException(ErrorCode.INVITE_CODE_NOT_FOUND));
        // 시작된 리그는 코드를 유지하므로 이미 시작된 리그임을 구분해 알린다.
        league.requireWaitingRoom();
        return new LeagueSummary(league.getId(), league.getName().getValue(),
                league.getStatus().name(),
                playerRepository.countByLeagueId(league.getId()),
                playerRepository.countByLeagueIdAndListedTrue(league.getId()));
    }

    /**
     * 리그 플레이어 목록과 리그 시작 가능 여부, 시작된 리그면 현재 거래일을 조회한다. 해당 리그에 입장한 회원만
     * 조회할 수 있다.
     *
     * <p>시작된 리그에서 탈퇴한 참여자는 '탈퇴한 사용자'로 표시한다 (member.md 진행 중인 리그 처리).
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @return 참여일시 순 플레이어 목록, 시작 가능 여부, 현재 거래일 (대기실이면 {@code null})
     * @throws NaspiBizException 리그가 없거나 (만료된 대기실 포함), 조회하는 회원이 리그에 입장하지 않은 경우
     */
    public LeaguePlayers getPlayers(long memberId, long leagueId) {
        League league = getLeague(leagueId);
        List<Player> players = playerRepository.findAllByLeagueId(leagueId);
        // 리그 참여 여부 확인
        boolean joined = players.stream()
                .anyMatch(player -> Objects.equals(player.getMemberId(), memberId));
        if (!joined) {
            throw new NaspiBizException(ErrorCode.FORBIDDEN);
        }

        // 리그 상세 정보 조회
        Map<Long, String> names = memberService.getNames(players.stream()
                .filter(player -> !player.isWithdrawn())
                .map(Player::getMemberId)
                .filter(Objects::nonNull)
                .toList());
        // 리그 참여자 상세 정보 조회
        List<LeaguePlayer> result = players.stream()
                .sorted(Comparator.comparing(Player::getJoinedAt).thenComparing(Player::getId))
                .map(player -> new LeaguePlayer(player.getId(), displayNameOf(player, names),
                        Objects.equals(player.getMemberId(), memberId),
                        player.getId().equals(league.getHostPlayerId()), player.isListed(),
                        player.getJoinedAt(), player.getListedAt()))
                .toList();
        // 이미 시작된 리그는 다시 시작할 수 없으므로 대기실일 때만 시작 가능으로 본다.
        boolean waiting = league.getStatus() == LeagueStatus.WAITING;
        boolean startable = waiting && League.canStart(players);
        // 시작된 리그도 초대 코드를 유지하지만 응답에는 대기실일 때만 내보낸다.
        return new LeaguePlayers(league.getId(), league.getName().getValue(),
                waiting ? league.getInviteCode().getValue() : null,
                waiting ? league.waitingRoomExpiresAt() : null, startable, tradingDayOf(league),
                result);
    }

    /**
     * 회원이 입장한 대기실·진행 중·종료 리그 목록을 조회한다. 저장된 리그 상태를 그대로 응답하며, 저장 상태가
     * 삭제·만료인 리그는 제외한다. 상장 전인 대기자가 입장한 대기실도 포함한다.
     *
     * @param memberId 조회하는 회원 ID
     * @return 회원의 입장 일시 최신순(같으면 리그 ID 내림차순) 리그 목록. 입장한 리그가 없으면 빈 목록
     */
    public List<MyLeague> getMyLeagues(long memberId) {
        List<Player> myPlayers = playerRepository.findAllByMemberIdAndLeagueStatusIn(memberId,
                Set.of(LeagueStatus.WAITING, LeagueStatus.STARTED, LeagueStatus.ENDED));
        if (myPlayers.isEmpty()) {
            return List.of();
        }
        List<Long> leagueIds = myPlayers.stream().map(Player::getLeagueId).toList();
        Map<Long, League> leagues = new HashMap<>();
        leagueRepository.findAllByIdIn(leagueIds)
                .forEach(league -> leagues.put(league.getId(), league));
        Map<Long, List<Player>> playersByLeague = new HashMap<>();
        playerRepository.findAllByLeagueIdIn(leagueIds).forEach(player -> playersByLeague
                .computeIfAbsent(player.getLeagueId(), id -> new ArrayList<>()).add(player));

        return myPlayers.stream()
                .sorted(Comparator.comparing(Player::getJoinedAt).reversed()
                        .thenComparing(Comparator.comparing(Player::getLeagueId).reversed()))
                .map(player -> toMyLeague(leagues.get(player.getLeagueId()), player,
                        playersByLeague.get(player.getLeagueId())))
                .toList();
    }

    private MyLeague toMyLeague(League league, Player me, List<Player> players) {
        boolean waiting = league.getStatus() == LeagueStatus.WAITING;
        return new MyLeague(league.getId(), league.getName().getValue(), league.getStatus(),
                players.size(), players.stream().filter(Player::isListed).count(),
                league.isHost(me), me.isListed(), me.getJoinedAt(), league.getCreatedAt(),
                waiting ? league.waitingRoomExpiresAt() : null, league.getStartedAt(),
                league.getEndsAt(),
                league.getStatus() == LeagueStatus.STARTED ? tradingDayOf(league) : null);
    }

    private String displayNameOf(Player player, Map<Long, String> names) {
        return player.isWithdrawn() ? Player.WITHDRAWN_DISPLAY_NAME
                : names.get(player.getMemberId());
    }

    /**
     * 시작된 리그가 지금 몇 거래일차인지 계산한다. 대기실이면 거래일이 없으므로 {@code null}이다.
     *
     * <p>정책 (docs/policies/v1/domain/league.md "리그 시작 및 운영", market.md "시장"): 거래일은 매일 09:00 KST에
     * 1일씩 늘어나고, 00:00~09:00에 시작한 리그는 당일 09:00까지도 1일차로 표시한다.
     *
     * <p>TODO: 종료 시각이 지나면 결과 페이지만 열려야 한다. 리그 종료(LEA-12)가 생기기 전까지 종료 시각 이후에는
     * 마지막 거래일(7일차)로 응답한다.
     */
    private LeagueTradingDay tradingDayOf(League league) {
        if (league.getStatus() == LeagueStatus.WAITING) {
            return null;
        }
        TradingSchedule schedule = league.tradingSchedule();
        int dayNumber = schedule.dayNumberAt(LocalDateTime.now(clock));
        return new LeagueTradingDay(dayNumber, TradingSchedule.TRADING_DAYS,
                schedule.periods().get(dayNumber - 1).endsAt(),
                league.getStartedAt(), league.getEndsAt());
    }

    /**
     * 거래할 수 있는 리그인지 검증한다. 조회용이라 락을 걸지 않는다. 거래 처리에는
     * {@code LeagueTradingService.lockForTrading}을 쓴다. 판정과 에러 코드는 접수·취소 경로와 같다.
     *
     * @param leagueId 리그 ID
     * @throws NaspiBizException 없거나 만료된 대기실이거나, 종료되었거나, 아직 시작하지 않은 리그인 경우
     */
    public void requireTradable(long leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
        league.requireTradable(LocalDateTime.now(clock));
    }

    /**
     * 리그에 입장한 회원의 플레이어 ID를 조회한다. 다른 모듈이 로그인 회원 본인의 플레이어를 찾을 때 쓴다
     * (클라이언트가 보낸 플레이어 ID를 믿지 않는다).
     *
     * @param memberId 회원 ID
     * @param leagueId 리그 ID
     * @return 플레이어 ID. 리그에 입장하지 않은 회원이면 빈 값. 탈퇴 후에는 해당 회원 ID로 로그인할 수 없다
     */
    public Optional<Long> findPlayerId(long memberId, long leagueId) {
        return playerRepository.findByLeagueIdAndMemberId(leagueId, memberId).map(Player::getId);
    }

    /**
     * 리그 플레이어들의 표시 이름을 조회한다. 리그 상태와 관계없이 조회하며, 입장 확인은 호출하는 쪽이
     * {@link #findPlayerId}로 한다.
     *
     * @param leagueId 리그 ID
     * @param playerIds 이름을 찾을 플레이어 ID 목록
     * @return 플레이어 ID별 이름. 탈퇴한 참여자는 '탈퇴한 사용자'. 이 리그에 없는 플레이어 ID는 빠진다
     */
    public Map<Long, String> getPlayerNames(long leagueId, Collection<Long> playerIds) {
        List<Player> players = playerRepository.findAllByLeagueId(leagueId).stream()
                .filter(player -> playerIds.contains(player.getId()))
                .toList();
        Map<Long, String> names = memberService.getNames(players.stream()
                .filter(player -> !player.isWithdrawn())
                .map(Player::getMemberId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, String> result = new HashMap<>();
        players.forEach(player -> result.put(player.getId(), displayNameOf(player, names)));
        return result;
    }

    /**
     * 진행 중인 리그의 현재 거래일을 조회한다. contents가 공시를 진행 중인 리그에서만 받고, 몇 거래일차에
     * 작성됐는지 기록할 때 쓴다.
     *
     * @param leagueId 리그 ID
     * @return 현재 거래일. 진행 중이 아닌 리그(대기실, 종료)면 빈 값
     * @throws NaspiBizException 리그가 없는 경우 (만료·삭제된 대기실 포함)
     */
    public Optional<LeagueTradingDay> findCurrentTradingDay(long leagueId) {
        League league = getLeague(leagueId);
        if (!league.isTradable()) {
            return Optional.empty();
        }
        return Optional.of(tradingDayOf(league));
    }

    /**
     * 회원이 참여 중인 진행 중(시작 후 종료 전) 리그의 플레이어를 모두 조회한다. 회원 탈퇴 처리에서 asset 등이
     * 탈퇴자의 리그별 플레이어를 찾을 때 쓴다. 탈퇴 트랜잭션 안에서 회원이 삭제되기 전에 호출해야 한다.
     *
     * @param memberId 회원 ID
     * @return 리그 ID와 플레이어 ID 목록. 진행 중인 리그에 참여하지 않았으면 빈 목록
     */
    public List<MemberPlayer> findStartedPlayersOfMember(long memberId) {
        return playerRepository.findAllByMemberIdAndLeagueStatus(memberId, LeagueStatus.STARTED)
                .stream()
                .map(player -> new MemberPlayer(player.getLeagueId(), player.getId()))
                .toList();
    }

    /**
     * 진행 중(시작 후 종료 전)인 리그의 ID를 모두 조회한다. market의 현재가 캐시 갱신에 쓴다.
     *
     * @return 진행 중인 리그 ID 목록
     */
    public List<Long> findStartedLeagueIds() {
        return leagueRepository.findIdsByStatus(LeagueStatus.STARTED);
    }

    /** 리그를 찾는다. 만료·삭제된 대기실은 접근할 수 없으므로 없는 리그로 취급한다. */
    private League getLeague(long leagueId) {
        return leagueRepository.findById(leagueId)
                .filter(found -> !found.isUnavailable())
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
    }
}
