package kr.ject.naspi.market.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
import kr.ject.naspi.league.application.LeagueQueryService;
import kr.ject.naspi.league.application.LeagueService;
import kr.ject.naspi.market.domain.Stock;
import kr.ject.naspi.market.domain.StockDescription;
import kr.ject.naspi.market.domain.StockName;
import kr.ject.naspi.market.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 수정.
 *
 * <p>정책 (docs/policies/v1/domain/market.md "종목 상장"): 본인이 상장한 종목만 수정할 수 있다.
 * 이름과 소개는 대기실에서만 함께 수정할 수 있고, 소개는 리그 시작 이후에도 수정할 수 있다.
 * 이름·소개 규칙은 상장할 때와 같다.
 */
@Service
@RequiredArgsConstructor
public class StockEditService {

    private final StockRepository stockRepository;
    private final LeagueService leagueService;
    private final LeagueQueryService leagueQueryService;

    /**
     * 대기실에서 종목 이름과 소개를 수정한다.
     *
     * <p>리그 행을 잠그고 대기실인지 확인하므로 리그 시작과 겹치지 않는다.
     *
     * @param leagueId 리그 ID
     * @param memberId 수정을 요청한 회원 ID
     * @param stockId 종목 ID
     * @param name 새 종목 이름 (앞뒤 공백은 제거된다)
     * @param description 새 종목 소개
     * @return 수정된 종목
     * @throws NaspiBizException 이름·소개가 규칙에 맞지 않거나, 종목이 없거나, 본인의 종목이 아니거나,
     *         이름이 같은 리그에서 중복되는 경우,
     *         만료·삭제된 대기실이거나 이미 시작된 리그이거나 리그에 입장하지 않은 경우
     */
    @Transactional
    public StockDetail editInLobby(long leagueId, long memberId, long stockId, String name,
            String description) {
        leagueService.lockWaitingRoom(leagueId);
        long playerId = resolvePlayerId(leagueId, memberId);
        StockName stockName = StockName.of(name);
        StockDescription stockDescription = StockDescription.of(description);
        Stock stock = findOwnedStock(leagueId, playerId, stockId);

        boolean nameChanged = !stock.getName().equals(stockName);
        if (nameChanged && stockRepository.existsByLeagueIdAndName(leagueId, stockName)) {
            throw new NaspiBizException(ErrorCode.STOCK_NAME_DUPLICATED);
        }

        stock.edit(stockName, stockDescription);
        return toDetail(stock);
    }

    /**
     * 대기실 또는 진행 중인 리그에서 종목 소개만 수정한다.
     *
     * <p>정책: 소개는 대기실과 리그 시작 이후 모두 수정할 수 있다. 종료된 리그에서는 수정할 수 없다.
     *
     * @param leagueId 리그 ID
     * @param memberId 수정을 요청한 회원 ID
     * @param stockId 종목 ID
     * @param description 새 종목 소개
     * @return 수정된 종목
     * @throws NaspiBizException 소개가 규칙에 맞지 않거나, 종목이 없거나, 본인의 종목이 아닌 경우,
     *         만료·삭제된 대기실이거나 종료된 리그이거나 리그에 입장하지 않은 경우
     */
    @Transactional
    public StockDetail changeDescription(long leagueId, long memberId, long stockId,
            String description) {
        leagueService.lockOngoing(leagueId);
        long playerId = resolvePlayerId(leagueId, memberId);
        StockDescription stockDescription = StockDescription.of(description);
        Stock stock = findOwnedStock(leagueId, playerId, stockId);

        stock.changeDescription(stockDescription);
        return toDetail(stock);
    }

    private long resolvePlayerId(long leagueId, long memberId) {
        return leagueQueryService.requireAccessiblePlayer(memberId, leagueId);
    }

    private Stock findOwnedStock(long leagueId, long playerId, long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .filter(found -> found.getLeagueId() == leagueId)
                .orElseThrow(() -> new NaspiBizException(ErrorCode.STOCK_NOT_FOUND));
        if (!stock.isListedBy(playerId)) {
            throw new NaspiBizException(ErrorCode.STOCK_NOT_OWNED);
        }
        return stock;
    }

    private static StockDetail toDetail(Stock stock) {
        return new StockDetail(stock.getId(), stock.getName().getValue(),
                stock.getDescription().getValue());
    }
}
