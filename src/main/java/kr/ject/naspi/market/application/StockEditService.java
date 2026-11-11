package kr.ject.naspi.market.application;

import kr.ject.naspi.global.exception.ErrorCode;
import kr.ject.naspi.global.exception.NaspiBizException;
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
 *
 * <p>TODO: league 구현 후 league 서비스를 직접 호출해 연동한다. 리그 상태 검증(이름·소개 수정은
 * 대기실만, 소개만 수정은 진행 중인 리그만)과 회원의 플레이어 ID 해석이 대상이다.
 */
@Service
@RequiredArgsConstructor
public class StockEditService {

    private final StockRepository stockRepository;

    /**
     * 대기실에서 종목 이름과 소개를 수정한다.
     *
     * <p>TODO: 리그가 대기실 상태인지 league에 확인한다 (ND-61).
     *
     * @param leagueId 리그 ID
     * @param memberId 수정을 요청한 회원 ID
     * @param stockId 종목 ID
     * @param name 새 종목 이름 (앞뒤 공백은 제거된다)
     * @param description 새 종목 소개
     * @return 수정된 종목
     * @throws NaspiBizException 이름·소개가 규칙에 맞지 않거나, 종목이 없거나, 본인의 종목이 아니거나,
     *         이름이 같은 리그에서 중복되는 경우
     */
    @Transactional
    public StockDetail editInLobby(long leagueId, long memberId, long stockId, String name,
            String description) {
        // TODO: league 연동 전까지 회원 ID를 플레이어 ID로 임시 사용한다.
        long playerId = memberId;
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
     * 진행 중인 리그에서 종목 소개만 수정한다.
     *
     * <p>TODO: 리그가 진행 중인지 league에 확인한다 (ND-66).
     *
     * @param leagueId 리그 ID
     * @param memberId 수정을 요청한 회원 ID
     * @param stockId 종목 ID
     * @param description 새 종목 소개
     * @return 수정된 종목
     * @throws NaspiBizException 소개가 규칙에 맞지 않거나, 종목이 없거나, 본인의 종목이 아닌 경우
     */
    @Transactional
    public StockDetail changeDescription(long leagueId, long memberId, long stockId,
            String description) {
        // TODO: league 연동 전까지 회원 ID를 플레이어 ID로 임시 사용한다.
        long playerId = memberId;
        StockDescription stockDescription = StockDescription.of(description);
        Stock stock = findOwnedStock(leagueId, playerId, stockId);

        stock.changeDescription(stockDescription);
        return toDetail(stock);
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
