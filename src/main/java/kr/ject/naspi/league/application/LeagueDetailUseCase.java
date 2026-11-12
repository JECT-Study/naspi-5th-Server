package kr.ject.naspi.league.application;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueDetail.DetailPlayer;
import kr.ject.naspi.league.application.LeagueDetail.PlayerStock;
import kr.ject.naspi.market.application.LeagueStock;
import kr.ject.naspi.market.application.StockQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 상세 조회. 리그의 플레이어 목록에 각 참여자가 상장한 종목 정보를 합친다.
 *
 * <p>종목은 하위 모듈인 market이 관리하므로 UseCase로 만들어 market의 종목 조회 서비스를 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeagueDetailUseCase {

    private final LeagueQueryService leagueQueryService;
    private final StockQueryService stockQueryService;

    /**
     * 리그 플레이어 목록과 각 참여자의 종목, 리그 시작 가능 여부를 조회한다. 해당 리그에 입장한 회원만 조회할 수 있다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @return 리그 상세 정보
     * @throws NaspiBizException 리그가 없거나, 조회하는 회원이 리그에 입장하지 않은 경우
     */
    public LeagueDetail getDetail(long memberId, long leagueId) {
        LeaguePlayers players = leagueQueryService.getPlayers(memberId, leagueId);
        Map<Long, LeagueStock> stocksByLister = stockQueryService.findAllByLeagueId(leagueId)
                .stream()
                .collect(Collectors.toMap(LeagueStock::listerPlayerId, Function.identity()));

        return new LeagueDetail(players.leagueId(), players.startable(), players.players().stream()
                .map(player -> new DetailPlayer(player.playerId(), player.name(), player.host(),
                        player.listed(), player.joinedAt(), player.listedAt(),
                        toPlayerStock(stocksByLister.get(player.playerId()))))
                .toList());
    }

    private static PlayerStock toPlayerStock(LeagueStock stock) {
        if (stock == null) {
            return null;
        }
        return new PlayerStock(stock.stockId(), stock.name(), stock.description());
    }
}
