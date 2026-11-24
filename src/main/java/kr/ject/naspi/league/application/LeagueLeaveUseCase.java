package kr.ject.naspi.league.application;

import java.util.List;
import java.util.Objects;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import kr.ject.naspi.market.application.StockListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기실 나가기.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "대기실", "리그 시작 및 운영"): 플레이어는 리그가 시작되기 전에
 * 대기실을 나갈 수 있고, 이때 상장한 종목은 삭제된다. 방장이 나가면 상장을 먼저 마친 참여자에게 방장이 넘어가고,
 * 넘겨받을 참여자가 없으면 대기실이 삭제된다. 리그가 시작된 이후에는 나갈 수 없다.
 *
 * <p>종목은 하위 모듈인 market이 관리하므로 UseCase로 만들어 market 서비스로 종목을 지운다.
 */
@Service
@RequiredArgsConstructor
public class LeagueLeaveUseCase {

    private final LeagueRepository leagueRepository;
    private final PlayerRepository playerRepository;
    private final StockListingService stockListingService;

    /**
     * 회원이 리그 대기실을 나간다. 나간 회원은 플레이어에서 빠지므로 초대 코드로 다시 입장할 수 있다.
     *
     * <p>리그 행을 잠그고 처리하므로 나가는 동안 리그가 시작되거나 다른 플레이어가 입장·퇴장하지 않는다.
     *
     * @param memberId 나가는 회원 ID
     * @param leagueId 리그 ID
     * @return 나간 리그와 대기실 삭제 여부
     * @throws NaspiBizException 리그가 없거나 만료·삭제된 대기실인 경우({@code LEAGUE_NOT_FOUND}),
     *         리그에 입장하지 않은 회원인 경우({@code FORBIDDEN}), 이미 시작·종료된 리그인 경우
     *         ({@code LEAGUE_ALREADY_STARTED})
     */
    @Transactional
    public LeftLeague leave(long memberId, long leagueId) {
        League league = leagueRepository.findWithLockById(leagueId)
                .filter(found -> !found.isUnavailable())
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));
        return leaveLocked(league, memberId);
    }

    /**
     * 탈퇴하는 회원을 소속된 모든 대기실에서 내보낸다. 나가기와 같이 상장한 종목은 삭제되고, 방장이면 방장이 위임된다.
     * 만료·삭제된 대기실과 이미 시작된 리그는 건너뛴다.
     *
     * <p>정책 (docs/policies/v1/domain/member.md "회원 탈퇴" - 대기실 처리).
     *
     * @param memberId 탈퇴하는 회원 ID
     */
    @Transactional
    public void leaveAllWaitingRooms(long memberId) {
        playerRepository.findLeagueIdsByMemberIdAndLeagueStatus(memberId, LeagueStatus.WAITING)
                .forEach(leagueId -> leagueRepository.findWithLockById(leagueId)
                        // 잠그기 전에 시작·만료됐을 수 있으므로 잠근 뒤 다시 확인한다.
                        .filter(League::isOpenForInvitation)
                        .ifPresent(league -> leaveLocked(league, memberId)));
    }

    private LeftLeague leaveLocked(League league, long memberId) {
        long leagueId = league.getId();
        List<Player> players = playerRepository.findAllByLeagueId(leagueId);
        Player leaver = players.stream()
                .filter(player -> Objects.equals(player.getMemberId(), memberId))
                .findFirst()
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));

        boolean waitingRoomDeleted = league.leave(leaver, players);
        stockListingService.delist(leagueId, leaver.getId());
        playerRepository.delete(leaver);
        return new LeftLeague(leagueId, waitingRoomDeleted);
    }
}
