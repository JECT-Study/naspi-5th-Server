package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
import kr.ject.naspi.league.domain.InviteCode;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
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
     * @throws NaspiBizException 초대 코드에 해당하는 대기실이 없는 경우
     *         (형식 오류, 이미 시작된 리그, 만료된 대기실 포함)
     */
    public LeagueSummary getSummary(String inviteCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        League league = leagueRepository.findByInviteCode(InviteCode.of(inviteCode))
                .filter(found -> found.isOpenForInvitation(now))
                .orElseThrow(() -> new NaspiBizException(ErrorCode.INVITE_CODE_NOT_FOUND));
        return new LeagueSummary(league.getId(), league.getName().getValue(),
                league.getStatus().name(),
                playerRepository.countByLeagueId(league.getId()),
                playerRepository.countByLeagueIdAndListedTrue(league.getId()));
    }

    /**
     * 리그 플레이어 목록과 리그 시작 가능 여부를 조회한다. 해당 리그에 입장한 회원만 조회할 수 있다.
     *
     * <p>TODO: 대기실에 있는 동안 탈퇴한 회원은 이름 없이 그대로 보여준다. 대기실 상태 탈퇴 처리(LEA-15)가
     * 정해지면 퇴장 처리 등에 맞춰 바꾼다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @return 참여일시 순 플레이어 목록과 시작 가능 여부
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
                .map(Player::getMemberId)
                .filter(Objects::nonNull)
                .toList());
        // 리그 참여자 상세 정보 조회
        List<LeaguePlayer> result = players.stream()
                .sorted(Comparator.comparing(Player::getJoinedAt).thenComparing(Player::getId))
                .map(player -> new LeaguePlayer(player.getId(), names.get(player.getMemberId()),
                        player.getId().equals(league.getHostPlayerId()), player.isListed(),
                        player.getJoinedAt(), player.getListedAt()))
                .toList();
        return new LeaguePlayers(league.getId(), startable(players), result);
    }

    /**
     * 리그를 시작할 수 있는지 확인한다.
     * 모든 플레이어가 상장을 마쳤으며, 상장을 마친 플레이어가 최소 인원 이상이어야 한다.
     */
    private static boolean startable(List<Player> players) {
        // 상장을 마친 플레이어 수
        long participants = players.stream().filter(Player::isListed).count();
        // 모든 플레이어가 상장을 마쳤는지 여부
        boolean noWaiting = players.stream().allMatch(Player::isListed);
        return participants >= League.MIN_PARTICIPANTS_TO_START && noWaiting;
    }

    /** 리그를 찾는다. 만료된 대기실은 접근할 수 없으므로 없는 리그로 취급한다. */
    private League getLeague(long leagueId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return leagueRepository.findById(leagueId)
                .filter(found -> !found.isExpired(now))
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
    }
}
