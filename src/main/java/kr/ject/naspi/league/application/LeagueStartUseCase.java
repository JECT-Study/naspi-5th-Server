package kr.ject.naspi.league.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueStarted.ListedStock;
import kr.ject.naspi.league.domain.League;
import kr.ject.naspi.league.domain.LeagueRepository;
import kr.ject.naspi.league.domain.LeagueStatus;
import kr.ject.naspi.league.domain.Player;
import kr.ject.naspi.league.domain.PlayerRepository;
import kr.ject.naspi.market.application.StockQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 시작.
 *
 * <p>정책 (docs/policies/v1/domain/league.md "리그 시작 및 운영"): 방장 포함 3명 이상의 참여자가 상장을 마치고
 * 대기자가 없으면 방장이 리그를 시작할 수 있다. 시작 권한은 방장에게만 있고, 시작 이후에는 새로 입장할 수 없다.
 * 모든 참여자의 자산 배분이 끝난 같은 시장 상태에서 거래를 시작한다.
 *
 * <p>시작 이벤트에 상장 종목 목록을 실어야 하는데 종목은 하위 모듈인 market이 관리하므로,
 * UseCase로 만들어 market의 종목 조회 서비스를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class LeagueStartUseCase {

    private final LeagueRepository leagueRepository;
    private final PlayerRepository playerRepository;
    private final StockQueryService stockQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 방장이 리그를 시작한다.
     *
     * <p>리그 행을 잠그고 처리하므로 시작하는 동안 다른 사용자가 입장하거나 대기실이 만료되지 않는다.
     * {@link LeagueStarted}를 같은 트랜잭션에서 발행하고, 초기 자산 배분이 실패하면 리그 시작도 함께 취소되어
     * 자산 배분이 끝나지 않은 리그에서 거래가 열리지 않는다.
     *
     * @param memberId 시작하는 회원 ID
     * @param leagueId 리그 ID
     * @return 시작한 리그
     * @throws NaspiBizException 리그가 없거나 만료된 대기실인 경우({@code LEAGUE_NOT_FOUND}),
     *         리그에 입장하지 않은 회원인 경우({@code FORBIDDEN}), 이미 시작된 리그인 경우
     *         ({@code LEAGUE_ALREADY_STARTED}), 방장이 아닌 경우({@code LEAGUE_HOST_ONLY}),
     *         시작 조건을 만족하지 않는 경우({@code LEAGUE_NOT_STARTABLE})
     */
    @Transactional
    public StartedLeague start(long memberId, long leagueId) {
        LocalDateTime now = LocalDateTime.now(clock);
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
        // 방장만 리그를 시작할 수 있다.
        if (!requester.getId().equals(league.getHostPlayerId())) {
            throw new NaspiBizException(ErrorCode.LEAGUE_HOST_ONLY);
        }

        league.start(players, now);
        // TODO: 이벤트 대신 UseCase에서 초기 자산 배분을 직접 호출한다. UseCase는 하위 모듈(asset)을 참조할 수
        //       있으므로 InitialAssetAllocationService.allocate()를 같은 트랜잭션에서 부르면 된다.
        //       바꾸면 LeagueStarted 이벤트와 asset의 LeagueStartedListener를 정리한다 (asset 작업과 맞춰 진행).
        eventPublisher.publishEvent(new LeagueStarted(leagueId, listedStocks(leagueId)));
        return new StartedLeague(leagueId, league.getStartedAt());
    }

    private List<ListedStock> listedStocks(long leagueId) {
        return stockQueryService.findAllByLeagueId(leagueId).stream()
                .map(stock -> new ListedStock(stock.stockId(), stock.listerPlayerId()))
                .toList();
    }
}
