package kr.ject.naspi.market.application;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.application.LeagueDetail.DetailPlayer;
import kr.ject.naspi.market.application.LeagueDetail.PlayerStock;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 상세 조회. league의 플레이어 목록에 market의 종목 정보를 합친다.
 *
 * <p>league는 market을 참조할 수 없으므로(의존 방향 league ← market) 종목까지 합친 조회는 market에 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeagueDetailService {

    private final LeagueQueryService leagueQueryService;
    private final StockRepository stockRepository;

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
        Map<Long, Stock> stocksByLister = stockRepository.findAllByLeagueId(leagueId).stream()
                .collect(Collectors.toMap(Stock::getListerPlayerId, Function.identity()));

        return new LeagueDetail(players.leagueId(), players.startable(), players.players().stream()
                .map(player -> new DetailPlayer(player.playerId(), player.name(), player.host(),
                        player.listed(), player.joinedAt(), player.listedAt(),
                        toPlayerStock(stocksByLister.get(player.playerId()))))
                .toList());
    }

    private static PlayerStock toPlayerStock(Stock stock) {
        if (stock == null) {
            return null;
        }
        return new PlayerStock(stock.getId(), stock.getName().getValue(),
                stock.getDescription().getValue());
    }
}
