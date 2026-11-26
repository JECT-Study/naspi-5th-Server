package kr.ject.naspi.market.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeaguePlayers.LeaguePlayer;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDailyPrice;
import kr.ject.naspi.market.domain.StockDailyPriceRepository;
import kr.ject.naspi.market.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리그 참여자의 종목 조회.
 *
 * <p>정책 (docs/policies/v1/domain/market.md "종목 상장"): 리그에 입장한 참여자는 같은 리그의 종목 이름과 소개를
 * 조회할 수 있다. 상장 전인 대기자도 입장한 상태이므로 조회할 수 있다. 시세(MKT-08)는 종목마다 거래일 번호가 가장 큰 시세 행과 종목 현재가로 만든다.
 * 리그 시작 전에는 시세가 없다.
 * 거래 로직용 조회는 {@link StockQueryService}를 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeagueStockLookupService {

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository dailyPriceRepository;
    private final LeagueQueryService leagueQueryService;

    /**
     * 리그의 종목 이름과 소개를 종목 ID 순으로 조회한다. 조회한 회원 본인의 종목에는 {@code mine}이 표시된다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @return 종목 목록. 상장된 종목이 없으면 빈 목록
     * @throws NaspiBizException 리그가 없거나 리그에 입장하지 않은 회원인 경우, 종료 시각이 지난 리그인 경우
     */
    public List<MemberStock> findAll(long memberId, long leagueId) {
        Viewer viewer = viewerOf(memberId, leagueId, latestPrices(leagueId));
        return stockRepository.findAllByLeagueId(leagueId).stream()
                .sorted(Comparator.comparing(Stock::getId))
                .map(viewer::toMemberStock)
                .toList();
    }

    /**
     * 리그의 종목 하나의 이름과 소개를 조회한다.
     *
     * @param memberId 조회하는 회원 ID
     * @param leagueId 리그 ID
     * @param stockId 종목 ID
     * @return 종목
     * @throws NaspiBizException 리그가 없거나 리그에 입장하지 않은 회원인 경우, 종료 시각이 지난 리그인 경우,
     *         리그에 해당 종목이 없는 경우
     *         ({@link ErrorCode#STOCK_NOT_FOUND})
     */
    public MemberStock findOne(long memberId, long leagueId, long stockId) {
        Viewer viewer = viewerOf(memberId, leagueId, latestPrices(leagueId));
        return stockRepository.findById(stockId)
                .filter(stock -> stock.getLeagueId() == leagueId)
                .map(viewer::toMemberStock)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.STOCK_NOT_FOUND));
    }

    /**
     * 조회하는 회원이 리그에 입장했는지 확인하고, 상장자 이름을 찾을 플레이어 목록과 본인 플레이어 ID를 모은다.
     */
    private Viewer viewerOf(long memberId, long leagueId,
            Map<Long, StockDailyPrice> pricesByStockId) {
        // 리그가 없거나(204) 입장하지 않은 회원이면(004) 여기서 예외가 난다.
        Map<Long, String> namesByPlayerId = new HashMap<>();
        for (LeaguePlayer player : leagueQueryService.getPlayers(memberId, leagueId).players()) {
            namesByPlayerId.put(player.playerId(), player.name());
        }
        // 종료 시각이 지난 리그면(212) 여기서 예외가 난다.
        long playerId = leagueQueryService.requireAccessiblePlayer(memberId, leagueId);
        return new Viewer(playerId, namesByPlayerId, pricesByStockId);
    }

    /**
     * 종목마다 거래일 번호가 가장 큰 시세 행을 모은다. 거래일 전환 처리 전에는 새 거래일 행이 아직 없으므로
     * 마지막으로 만들어진 행이 적용 중인 기준가다. 종료된 리그는 마지막 거래일 행이 남는다.
     */
    private Map<Long, StockDailyPrice> latestPrices(long leagueId) {
        Map<Long, StockDailyPrice> latest = new HashMap<>();
        for (StockDailyPrice price : dailyPriceRepository.findAllByLeagueId(leagueId)) {
            latest.merge(price.getStockId(), price, (a, b) ->
                    a.getTradingDay() >= b.getTradingDay() ? a : b);
        }
        return latest;
    }

    private record Viewer(long playerId, Map<Long, String> namesByPlayerId,
            Map<Long, StockDailyPrice> pricesByStockId) {

        MemberStock toMemberStock(Stock stock) {
            StockDailyPrice price = pricesByStockId.get(stock.getId());
            StockQuote quote = price == null || stock.getCurrentPrice() == null
                    ? null : StockQuote.of(stock, price);
            return new MemberStock(stock.getId(), stock.getName().getValue(),
                    stock.getDescription().getValue(),
                    namesByPlayerId.get(stock.getListerPlayerId()),
                    stock.isListedBy(playerId), quote);
        }
    }
}
