package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueName;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기실에서 방장만 할 수 있는 리그 관리: 리그 이름 수정, 리그 삭제, 플레이어 추방.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "대기실"): 방장과 참여자의 권한은 대기실에서만 다르다.
 * 방장은 리그 이름 수정, 리그 삭제, 참여자 삭제를 할 수 있고, 리그가 시작된 이후에는 모두 같은 권한을 가진다.
 *
 * <p>추방된 플레이어의 종목은 하위 모듈인 market이 관리하므로 UseCase로 만든다.
 * 종목 삭제는 market에 서비스가 생길 때까지 TODO로 남겨 둔다 ({@link LeagueLeaveUseCase}와 같음).
 */
@Service
@RequiredArgsConstructor
public class LeagueHostUseCase {

    private final LeagueRepository leagueRepository;
    private final PlayerRepository playerRepository;
    private final Clock clock;

    /**
     * 방장이 리그 이름을 바꾼다.
     *
     * @param memberId 요청한 회원 ID
     * @param leagueId 리그 ID
     * @param name 새 리그 이름 (1~12자, 다른 리그와 중복 가능)
     * @return 이름을 바꾼 리그
     * @throws NaspiBizException 리그가 없거나 만료·삭제된 대기실인 경우({@code LEAGUE_NOT_FOUND}),
     *         리그에 입장하지 않은 회원인 경우({@code FORBIDDEN}), 이미 시작된 리그인 경우
     *         ({@code LEAGUE_ALREADY_STARTED}), 방장이 아닌 경우({@code LEAGUE_HOST_ONLY}),
     *         이름 규칙에 맞지 않는 경우({@code INVALID_LEAGUE_NAME})
     */
    @Transactional
    public RenamedLeague rename(long memberId, long leagueId, String name) {
        LocalDateTime now = LocalDateTime.now(clock);
        HostContext context = loadAsHost(memberId, leagueId, now);
        context.league().rename(LeagueName.of(name), now);
        return new RenamedLeague(leagueId, context.league().getName().getValue());
    }

    /**
     * 방장이 대기실을 삭제한다. 초대 코드가 폐기되고, 남아 있던 플레이어도 더는 접근할 수 없다.
     *
     * @param memberId 요청한 회원 ID
     * @param leagueId 리그 ID
     * @throws NaspiBizException 리그가 없거나 만료·삭제된 대기실인 경우({@code LEAGUE_NOT_FOUND}),
     *         리그에 입장하지 않은 회원인 경우({@code FORBIDDEN}), 이미 시작된 리그인 경우
     *         ({@code LEAGUE_ALREADY_STARTED}), 방장이 아닌 경우({@code LEAGUE_HOST_ONLY})
     */
    @Transactional
    public void delete(long memberId, long leagueId) {
        LocalDateTime now = LocalDateTime.now(clock);
        loadAsHost(memberId, leagueId, now).league().delete(now);
    }

    /**
     * 방장이 대기실에서 다른 플레이어를 추방한다. 상장을 마친 참여자와 상장 전인 대기자 모두 추방할 수 있다.
     * 추방된 플레이어는 대기실을 나간 것과 같이 플레이어에서 빠진다.
     *
     * @param memberId 요청한 회원 ID
     * @param leagueId 리그 ID
     * @param playerId 추방할 플레이어 ID
     * @throws NaspiBizException 리그가 없거나 만료·삭제된 대기실인 경우({@code LEAGUE_NOT_FOUND}),
     *         리그에 입장하지 않은 회원인 경우({@code FORBIDDEN}), 이미 시작된 리그인 경우
     *         ({@code LEAGUE_ALREADY_STARTED}), 방장이 아닌 경우({@code LEAGUE_HOST_ONLY}),
     *         리그에 없는 플레이어인 경우({@code PLAYER_NOT_FOUND}), 방장 자신을 추방하려는 경우
     *         ({@code LEAGUE_HOST_NOT_KICKABLE})
     */
    @Transactional
    public void kick(long memberId, long leagueId, long playerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        HostContext context = loadAsHost(memberId, leagueId, now);
        Player target = context.players().stream()
                .filter(player -> player.getId() == playerId)
                .findFirst()
                .orElseThrow(() -> new NaspiBizException(ErrorCode.PLAYER_NOT_FOUND));

        context.league().kick(target, now);
        // TODO: 추방된 플레이어의 종목 삭제. market에 상장자 플레이어 ID로 종목을 지우는 서비스가 생기면 여기서 호출한다
        //       (LeagueLeaveUseCase와 같은 서비스를 쓴다).
        playerRepository.delete(target);
    }

    /**
     * 리그 행을 잠그고, 요청한 회원이 대기실의 방장인지 확인한다. 잠근 채 처리하므로 그사이 리그가 시작되거나
     * 방장이 바뀌지 않는다.
     */
    private HostContext loadAsHost(long memberId, long leagueId, LocalDateTime now) {
        League league = leagueRepository.findWithLockById(leagueId)
                .filter(found -> !found.isUnavailable(now))
                .orElseThrow(() -> new NaspiBizException(ErrorCode.LEAGUE_NOT_FOUND));

        List<Player> players = playerRepository.findAllByLeagueId(leagueId);
        Player requester = players.stream()
                .filter(player -> Objects.equals(player.getMemberId(), memberId))
                .findFirst()
                .orElseThrow(() -> new NaspiBizException(ErrorCode.FORBIDDEN));

        // 시작 후에는 모두 같은 권한이므로, 방장 확인보다 먼저 이미 시작된 리그인지 알린다.
        if (league.getStatus() != LeagueStatus.WAITING) {
            throw new NaspiBizException(ErrorCode.LEAGUE_ALREADY_STARTED);
        }
        if (!league.isHost(requester)) {
            throw new NaspiBizException(ErrorCode.LEAGUE_HOST_ONLY);
        }
        return new HostContext(league, players);
    }

    private record HostContext(League league, List<Player> players) {
    }
}
